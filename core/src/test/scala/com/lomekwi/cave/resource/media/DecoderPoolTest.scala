package com.lomekwi.cave.resource.media

import com.lomekwi.cave.pipeline.audio.AudFrame
import com.lomekwi.cave.resource.decoder.DecRes

class DecoderPoolTest extends munit.FunSuite {

  private val host: MedRes = new MedRes("decoder-pool-test") {
    override protected def newDecoder(): DecRes[?] = new FakeDecRes(this)
    override protected def generateMetadata(metadataDecRes: DecRes[?]): Unit = {}
  }

  private class FakeDecRes(segment: MedRes) extends DecRes[AudFrame](segment) {
    var closed = false
    var position: Long = 0
    override def start(): Unit = initialized = true
    override def close(): Unit = closed = true
    override protected def configure(): Unit = {}
    override def grab() = null
    override def sync(time: Long): Unit = position = time
    override def get(time: Long, frame: AudFrame): Unit = position = time
    override def seek(time: Long): Unit = position = time
    override def codecName: String = "fake"
    override def codec: Int = 0
    override def lengthPerFrame: Long = 1
    override def getLengthInTime: Long = 0
  }

  private def newPool(weight: Double): DecoderPool =
    new DecoderPool(() => new FakeDecRes(host), weight)

  test("相同键复用同一个解码器") {
    val pool = newPool(0.1)
    val first = pool.acquire("a")
    val dec1 = first.dec
    first.close()
    val second = pool.acquire("a")
    assert((dec1) eq (second.dec))
    second.close()
    pool.disposeAll()
  }

  test("不同键分配不同解码器") {
    val pool = newPool(0.1)
    val a = pool.acquire("a")
    val b = pool.acquire("b")
    assert(a.dec ne b.dec)
    a.close()
    b.close()
    pool.disposeAll()
  }

  test("交替借用时各键状态互不串扰") {
    // 模拟转场两侧交替使用，两个键的状态互不串扰
    val pool = newPool(0.1)
    val keyA = new Object
    val keyB = new Object
    val la = pool.acquire(keyA)
    la.dec.asInstanceOf[FakeDecRes].position = 100
    la.close()
    val lb = pool.acquire(keyB)
    lb.dec.asInstanceOf[FakeDecRes].position = 200
    lb.close()
    val la2 = pool.acquire(keyA)
    assertEquals(la2.dec.asInstanceOf[FakeDecRes].position, 100L)
    val lb2 = pool.acquire(keyB)
    assertEquals(lb2.dec.asInstanceOf[FakeDecRes].position, 200L)
    la2.close()
    lb2.close()
    pool.disposeAll()
  }

  test("同一键重复借用被拒绝") {
    val pool = newPool(0.1)
    val lease = pool.acquire("a")
    intercept[IllegalStateException] { pool.acquire("a") }
    lease.close()
    pool.disposeAll()
  }

  test("驱逐空闲项时只关闭空闲解码器") {
    val pool = newPool(0.1)
    val active = pool.acquire("active")
    val idle = pool.acquire("idle")
    val idleDec = idle.dec.asInstanceOf[FakeDecRes]
    idle.close()
    // 归还后的条目拨到超龄，活跃条目不在空闲列表里，不会被驱逐触碰
    pool.idleEntries.foreach { case (_, pd) => pd.lastAccessNanos = 0L }
    pool.evictIdle()
    assert(idleDec.closed)
    assert(!active.dec.asInstanceOf[FakeDecRes].closed)
    val fresh = pool.acquire("idle")
    assert(fresh.dec.asInstanceOf[FakeDecRes] ne idleDec)
    active.close()
    fresh.close()
    pool.disposeAll()
  }

  test("disposeAll 关闭包括活跃项在内的全部解码器") {
    val pool = newPool(0.1)
    val a = pool.acquire("a")
    val b = pool.acquire("b")
    b.close()
    pool.disposeAll()
    assert(a.dec.asInstanceOf[FakeDecRes].closed)
    assert(b.dec.asInstanceOf[FakeDecRes].closed)
  }

  test("超出预算时准入会驱逐最老的空闲池") {
    val loaded = (0 until 8).map(_ => {
      val p = newPool(1.0)
      (p, p.acquire("k"))
    })
    // 满载后新建，全局无空闲可回收，仍放行借出
    val extra = newPool(1.0)
    val extraLease = extra.acquire("k")
    assert(extraLease.dec != null)
    // 释放最老的一格空闲，下一个超预算的新建应把它回收
    val released = loaded.head
    released._2.close()
    val extra2 = newPool(1.0)
    val l2 = extra2.acquire("k")
    assert(released._2.dec.asInstanceOf[FakeDecRes].closed)
    l2.close()
    extraLease.close()
    extra.disposeAll()
    extra2.disposeAll()
    loaded.foreach { case (p, l) =>
      l.close()
      p.disposeAll()
    }
  }
}
