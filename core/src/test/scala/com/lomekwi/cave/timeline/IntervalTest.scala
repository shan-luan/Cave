package com.lomekwi.cave.timeline

class IntervalTest extends munit.FunSuite {

  test("contains 不包含上端点") {
    val r = 100 ~~ 200
    assert(r.contains(100))
    assert(r.contains(199))
    assert(!(r.contains(200)))
    assert(!(r.contains(99)))
  }

  test("空区间不包含任何时间点") {
    val r = 100 ~~ 100
    assert(r.isEmpty)
    assert(!(r.contains(100)))
  }

  test("intersects 要求存在公共点") {
    assert((0 ~~ 10).intersects(5 ~~ 15))
    assert(!((0 ~~ 10).intersects(10 ~~ 20)))
  }

  test("交集返回公共部分") {
    assertEquals((0 ~~ 10) & (5 ~~ 15), Some(5 ~~ 10))
    assertEquals((0 ~~ 10) & (-5 ~~ 10), Some(0 ~~ 10))
    assertEquals((0 ~~ 10) & (2 ~~ 8), Some(2 ~~ 8))
    assertEquals((2 ~~ 8) & (0 ~~ 10), (0 ~~ 10) & (2 ~~ 8))
  }

  test("不相交或仅相接时交集为空") {
    assertEquals((0 ~~ 10) & (10 ~~ 20), None)
    assertEquals((0 ~~ 10) & (20 ~~ 30), None)
    assertEquals((20 ~~ 30) & (0 ~~ 10), None)
    assert(((0 ~~ 10) & (20 ~~ 30)).isEmpty)
    assert(!(((0 ~~ 10) & (9 ~~ 30)).isEmpty))
  }

  test("与空区间求交为空") {
    assertEquals((0 ~~ 10) & (5 ~~ 5), None)
    assertEquals((5 ~~ 5) & (0 ~~ 10), None)
    assertEquals((0 ~~ 0) & (0 ~~ 0), None)
  }

  test("isConnected 把相邻也算作连通") {
    assert((0 ~~ 10).isConnected(5 ~~ 15))
    assert((0 ~~ 10).isConnected(10 ~~ 20))
    assert(!((0 ~~ 10).isConnected(11 ~~ 20)))
  }

  test("shift 同时移动两个端点") {
    assertEquals((100 ~~ 200).shift(10), 110 ~~ 210)
    assertEquals((100 ~~ 200).shift(-10), 90 ~~ 190)
  }

  test("排序按字典序") {
    val sorted = List(10 ~~ 20, 0 ~~ 5, 0 ~~ 3).sorted
    assertEquals(sorted, List(0 ~~ 3, 0 ~~ 5, 10 ~~ 20))
  }
}
