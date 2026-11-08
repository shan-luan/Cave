package com.lomekwi.cave.timeline

import com.lomekwi.cave.project.{IdSource, TestProject}

/**
 * 转场拓扑的模型级测试。
 */
class TrackTransitionTest extends GdxTestBase {

  private var project: TestProject = null
  private var timeline: TestTimeline = null

  override def beforeEach(context: BeforeEach): Unit = {
    project = new TestProject()
    timeline = project.timeline
  }

  private def newSegment(duration: Long): Content = new TestCont(duration)

  private def at(value: Long): Instant = Instant.At(Timespan(value))

  private def span(value: Long): Timespan = Timespan(value)

  extension (lo: Int)
    private def ~~(hi: Int): Interval = Interval(at(lo), at(hi))

  private def place(track: Track, segment: Content, interval: Interval, origin: Instant = Instant.Zero): Unit = {
    timeline.add(track, segment, interval, origin)
  }

  private def transitionOf(track: Track, time: Long): Transition = track.get(at(time)) match {
    case t: Transition => t
    case _ => null
  }

  /** 不能构造转场的内容。 */
  private class NoTransitionCont(duration: Long) extends TestCont(duration) {
    override def canCreateTransitionWith(other: Content): Boolean = false

    override def createTransition(other: Content)(using IdSource): Transition =
      throw new UnsupportedOperationException("转场不应由本片段构造")
  }

  test("重叠内容保留完整区间并共享转场") {
    def t0: Track = timeline.getTrackOrCreate(0)
    val a = newSegment(1000)
    val b = newSegment(1000)
    place(t0, a, 0 ~~ 1000)
    place(t0, b, 500 ~~ 1500)

    assertEquals(t0.interval(a), 0 ~~ 1000)
    assertEquals(t0.interval(b), 500 ~~ 1500)
    assertEquals(t0.interval(transitionOf(t0, 600)), 500 ~~ 1000)
  }

  test("get 按内容-转场-内容遍历") {
    def t0: Track = timeline.getTrackOrCreate(0)
    val a = newSegment(1000)
    val b = newSegment(1000)
    place(t0, a, 0 ~~ 1000)
    place(t0, b, 500 ~~ 1500)
    val transition = transitionOf(t0, 600)
    assert((transition) != null)

    assert((a) eq (t0.get(at(499))))
    assert((transition) eq (t0.get(at(500))))
    assert((transition) eq (t0.get(at(999))))
    assert((b) eq (t0.get(at(1000))))
    assert((b) eq (t0.get(at(1499))))
    assert(t0.get(at(1500)).isInstanceOf[Gap])
  }

  test("intervalAt 优先返回转场而非覆盖它的内容") {
    def t0: Track = timeline.getTrackOrCreate(0)
    val a = newSegment(1000)
    val b = newSegment(1000)
    place(t0, a, 0 ~~ 1000)
    place(t0, b, 500 ~~ 1500)

    assertEquals(t0.intervalAt(at(100)), 0 ~~ 1000)
    assertEquals(t0.intervalAt(at(600)), 500 ~~ 1000)
    assertEquals(t0.intervalAt(at(1200)), 500 ~~ 1500)
  }

  test("三方重叠被拒绝") {
    def t0: Track = timeline.getTrackOrCreate(0)
    val a = newSegment(1000)
    val b = newSegment(1000)
    val c = newSegment(1000)
    place(t0, a, 0 ~~ 1000)
    place(t0, b, 500 ~~ 1500)

    assert(!timeline.add(t0, c, 800 ~~ 1800, Instant.Zero))
    assertEquals(t0.size, 3)
  }

  test("重叠不能吞并邻居") {
    def t0: Track = timeline.getTrackOrCreate(0)
    val a = newSegment(100000)
    val b = newSegment(1000)
    place(t0, a, 0 ~~ 1000)
    place(t0, b, 500 ~~ 1500)

    val applied: Timespan = timeline.setEnd(Seq(a), span(100000))

    assertEquals(applied, span(499))
    assertEquals(t0.interval(a), 0 ~~ 1499)
    assertEquals(t0.interval(b), 500 ~~ 1500)
  }

  test("终点扩展在第二个后继前停止") {
    def t0: Track = timeline.getTrackOrCreate(0)
    val a = newSegment(100000)
    val b = newSegment(100000)
    val c = newSegment(100000)
    place(t0, a, 0 ~~ 1000)
    place(t0, b, 500 ~~ 2000)
    place(t0, c, 1500 ~~ 2500)

    val applied: Timespan = timeline.setEnd(Seq(a), span(100000))

    assertEquals(applied, span(500))
    assertEquals(t0.interval(a), 0 ~~ 1500)
  }

  test("未受影响的转场保持对象身份") {
    def t0: Track = timeline.getTrackOrCreate(0)
    val a = newSegment(1000)
    val b = newSegment(1000)
    val c = newSegment(1000)
    place(t0, a, 0 ~~ 1000)
    place(t0, b, 500 ~~ 1500)
    place(t0, c, 2000 ~~ 3000)
    val transition = transitionOf(t0, 600)
    assert((transition) != null)

    timeline.moveTime(Seq(c), span(100))

    val after = timeline.getTrackOrCreate(0)
    assertEquals(after.interval(c), 2100 ~~ 3100)
    assert((transition) eq (transitionOf(after, 600)))
  }

  test("删除转场时把重叠区对半分割") {
    def t0: Track = timeline.getTrackOrCreate(0)
    val a = newSegment(1000)
    val b = newSegment(1000)
    place(t0, a, 0 ~~ 1000)
    place(t0, b, 500 ~~ 1500)
    val transition = transitionOf(t0, 600)
    assert((transition) != null)

    timeline.remove(transition)

    val after = timeline.getTrackOrCreate(0)
    assertEquals(after.interval(a), 0 ~~ 750)
    assertEquals(after.interval(b), 750 ~~ 1500)
    assert((transitionOf(after, 700)) == null)
  }

  test("删除内容不影响两侧邻居") {
    def t0: Track = timeline.getTrackOrCreate(0)
    val a = newSegment(1000)
    val b = newSegment(1000)
    val c = newSegment(1000)
    place(t0, a, 0 ~~ 1000)
    place(t0, b, 1500 ~~ 2500)
    place(t0, c, 2000 ~~ 3000)

    timeline.remove(b)

    val after = timeline.getTrackOrCreate(0)
    assertEquals(after.interval(a), 0 ~~ 1000)
    assertEquals(after.interval(c), 2000 ~~ 3000)
    assert((transitionOf(after, 600)) == null)
  }

  test("收缩内容不能落入左邻居内部") {
    def t0: Track = timeline.getTrackOrCreate(0)
    val a = newSegment(100000)
    val c = newSegment(100000)
    val b = newSegment(100000)
    place(t0, a, 0 ~~ 1000)
    place(t0, c, 500 ~~ 2000)
    place(t0, b, 1200 ~~ 2500)

    val applied: Timespan = timeline.setEnd(Seq(c), span(-100000))

    assertEquals(applied, span(-999))
    assertEquals(t0.interval(c), 500 ~~ 1001)
    assertEquals(t0.interval(a), 0 ~~ 1000)
  }

  test("左侧无法创建转场时回退到右侧") {
    def t0: Track = timeline.getTrackOrCreate(0)
    val a = new NoTransitionCont(1000)
    val b = newSegment(1000)
    place(t0, a, 0 ~~ 1000)
    place(t0, b, 500 ~~ 1500)

    assert((transitionOf(t0, 600)) != null)
  }

  test("右侧无法创建转场时仍使用左侧") {
    def t0: Track = timeline.getTrackOrCreate(0)
    val a = newSegment(1000)
    val b = new NoTransitionCont(1000)
    place(t0, a, 0 ~~ 1000)
    place(t0, b, 500 ~~ 1500)

    assert((transitionOf(t0, 600)) != null)
  }

  test("两侧都无法创建转场时重叠被拒绝") {
    def t0: Track = timeline.getTrackOrCreate(0)
    val a = new NoTransitionCont(1000)
    val b = new NoTransitionCont(1000)
    place(t0, a, 0 ~~ 1000)

    assert(!timeline.add(t0, b, 500 ~~ 1500, Instant.Zero))
    assertEquals(t0.size, 1)
  }
}
