package com.lomekwi.cave.timeline

class IntervalTest extends munit.FunSuite {

  private def at(value: Long): Instant = Instant.At(Timespan(value))

  test("contains 不包含上端点") {
    val r = at(100) ~~ at(200)
    assert(r.contains(at(100)))
    assert(r.contains(at(199)))
    assert(!(r.contains(at(200))))
    assert(!(r.contains(at(99))))
  }

  test("空区间不包含任何时间点") {
    val r = at(100) ~~ at(100)
    assert(r.isEmpty)
    assert(!(r.contains(at(100))))
  }

  test("intersects 要求存在公共点") {
    assert((at(0) ~~ at(10)).intersects(at(5) ~~ at(15)))
    assert(!((at(0) ~~ at(10)).intersects(at(10) ~~ at(20))))
  }

  test("交集返回公共部分") {
    assertEquals((at(0) ~~ at(10)) & (at(5) ~~ at(15)), Some(at(5) ~~ at(10)))
    assertEquals((at(0) ~~ at(10)) & (at(-5) ~~ at(10)), Some(at(0) ~~ at(10)))
    assertEquals((at(0) ~~ at(10)) & (at(2) ~~ at(8)), Some(at(2) ~~ at(8)))
    assertEquals((at(2) ~~ at(8)) & (at(0) ~~ at(10)), (at(0) ~~ at(10)) & (at(2) ~~ at(8)))
  }

  test("不相交或仅相接时交集为空") {
    assertEquals((at(0) ~~ at(10)) & (at(10) ~~ at(20)), None)
    assertEquals((at(0) ~~ at(10)) & (at(20) ~~ at(30)), None)
    assertEquals((at(20) ~~ at(30)) & (at(0) ~~ at(10)), None)
    assert(((at(0) ~~ at(10)) & (at(20) ~~ at(30))).isEmpty)
    assert(!(((at(0) ~~ at(10)) & (at(9) ~~ at(30))).isEmpty))
  }

  test("与空区间求交为空") {
    assertEquals((at(0) ~~ at(10)) & (at(5) ~~ at(5)), None)
    assertEquals((at(5) ~~ at(5)) & (at(0) ~~ at(10)), None)
    assertEquals((at(0) ~~ at(0)) & (at(0) ~~ at(0)), None)
  }

  test("isConnected 把相邻也算作连通") {
    assert((at(0) ~~ at(10)).isConnected(at(5) ~~ at(15)))
    assert((at(0) ~~ at(10)).isConnected(at(10) ~~ at(20)))
    assert(!((at(0) ~~ at(10)).isConnected(at(11) ~~ at(20))))
  }

  test("shift 同时移动两个端点") {
    assertEquals((at(100) ~~ at(200)).shift(Timespan(10)), at(110) ~~ at(210))
    assertEquals((at(100) ~~ at(200)).shift(Timespan(-10)), at(90) ~~ at(190))
  }

  test("+ 返回两个区间的凸包") {
    assertEquals((at(0) ~~ at(10)) + (at(5) ~~ at(15)), at(0) ~~ at(15))
    assertEquals((at(0) ~~ at(10)) + (at(20) ~~ at(30)), at(0) ~~ at(30))
    assertEquals((at(20) ~~ at(30)) + (at(0) ~~ at(10)), at(0) ~~ at(30))
    assertEquals((at(0) ~~ at(10)) + (at(2) ~~ at(8)), at(0) ~~ at(10))
    assertEquals((at(2) ~~ at(8)) + (at(0) ~~ at(10)), at(0) ~~ at(10))
  }

  test("~+ 从起点向后构造区间") {
    assertEquals(at(100) ~+ Timespan(30), at(100) ~~ at(130))
    assertEquals(at(100) ~+ Timespan.Zero, at(100) ~~ at(100))
    assertEquals(at(100) ~+ Timespan(-30), at(70) ~~ at(100))
  }

  test("~- 从起点向前构造区间") {
    assertEquals(at(100) ~- Timespan(30), at(70) ~~ at(100))
    assertEquals(at(100) ~- Timespan.Zero, at(100) ~~ at(100))
    assertEquals(at(100) ~- Timespan(-30), at(100) ~~ at(130))
  }

  test("排序按字典序") {
    val sorted = List(at(10) ~~ at(20), at(0) ~~ at(5), at(0) ~~ at(3)).sorted
    assertEquals(sorted, List(at(0) ~~ at(3), at(0) ~~ at(5), at(10) ~~ at(20)))
  }
}
