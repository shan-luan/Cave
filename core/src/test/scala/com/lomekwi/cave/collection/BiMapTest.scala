package com.lomekwi.cave.collection

import scala.collection.immutable

class BiMapTest extends munit.FunSuite {

  private def sample() =
    BiMap(immutable.Map.empty[Int, String], immutable.Map.empty[String, Int])
      .updated(1, "one")
      .updated(2, "two")

  test("apply 接受空映射并建立双向索引") {
    val bimap = sample()
    assertEquals(bimap.forward, immutable.Map(1 -> "one", 2 -> "two"))
    assertEquals(bimap.reverse, immutable.Map("one" -> 1, "two" -> 2))
  }

  test("apply 拒绝非空映射") {
    intercept[IllegalArgumentException] { BiMap(immutable.Map(1 -> "one"), immutable.Map.empty[String, Int]) }
    intercept[IllegalArgumentException] { BiMap(immutable.Map.empty[Int, String], immutable.Map("one" -> 1)) }
  }

  test("操作保留具体映射类型") {
    val bimap = BiMap(
      immutable.HashMap.empty[Int, String],
      immutable.TreeMap.empty[String, Int]
    ).updated(2, "two").updated(1, "one").updated(3, "three")

    assert(bimap.forward.isInstanceOf[immutable.HashMap[?, ?]])
    assert(bimap.reverse.isInstanceOf[immutable.TreeMap[?, ?]])
    assertEquals(bimap.reverse.keys.toList, List("one", "three", "two"))
  }

  test("get 支持双向查找") {
    val bimap = sample()
    assertEquals(bimap.get(1), Some("one"))
    assertEquals(bimap.getKey("two"), Some(2))
  }

  test("键或值缺失时 get 返回 none") {
    val bimap = sample()
    assertEquals(bimap.get(3), None)
    assertEquals(bimap.getKey("three"), None)
  }

  test("contains 双向检查") {
    val bimap = sample()
    assert(bimap.contains(1))
    assert(!(bimap.contains(3)))
    assert(bimap.containsValue("two"))
    assert(!(bimap.containsValue("four")))
  }

  test("size 与 isEmpty 反映正向映射") {
    assert(BiMap.empty[Int, String].isEmpty)
    assertEquals(BiMap.empty[Int, String].size, 0)
    assertEquals(sample().size, 2)
    assert(!(sample().isEmpty))
  }

  test("updated 向两侧映射加入新绑定") {
    val bimap = sample().updated(3, "three")
    assertEquals(bimap.get(3), Some("three"))
    assertEquals(bimap.getKey("three"), Some(3))
    assertEquals(bimap.size, 3)
  }

  test("updated 重绑键并驱逐旧值") {
    val bimap = sample().updated(1, "uno")
    assertEquals(bimap.get(1), Some("uno"))
    assertEquals(bimap.getKey("uno"), Some(1))
    assertEquals(bimap.getKey("one"), None)
    assertEquals(bimap.size, 2)
  }

  test("updated 重绑值并驱逐旧键") {
    val bimap = sample().updated(3, "one")
    assertEquals(bimap.get(3), Some("one"))
    assertEquals(bimap.getKey("one"), Some(3))
    assertEquals(bimap.get(1), None)
    assertEquals(bimap.size, 2)
  }

  test("两侧都冲突时 updated 同时驱逐") {
    val bimap = sample().updated(2, "one")
    assertEquals(bimap.forward, immutable.Map(2 -> "one"))
    assertEquals(bimap.reverse, immutable.Map("one" -> 2))
  }

  test("重复绑定现有项时内容不变") {
    val bimap = sample()
    val reupdated = bimap.updated(1, "one")
    assertEquals(reupdated.forward, bimap.forward)
    assertEquals(reupdated.reverse, bimap.reverse)
  }

  test("plus 接受元组") {
    val bimap = sample() + (3 -> "three")
    assertEquals(bimap.getKey("three"), Some(3))
  }

  test("拼接按顺序折叠条目") {
    val bimap = sample() ++ List(3 -> "three", 1 -> "uno")
    assertEquals(bimap.forward, immutable.Map(1 -> "uno", 2 -> "two", 3 -> "three"))
    assertEquals(bimap.reverse, immutable.Map("uno" -> 1, "two" -> 2, "three" -> 3))
  }

  test("移除绑定时从两侧映射同时删除") {
    val bimap = sample().removed(1)
    assertEquals(bimap.get(1), None)
    assertEquals(bimap.getKey("one"), None)
    assertEquals(bimap.forward, immutable.Map(2 -> "two"))
  }

  test("移除不存在的键返回原实例") {
    val bimap = sample()
    assert((bimap) eq (bimap.removed(3)))
    assert((bimap) eq (bimap - 3))
  }

  test("按值移除时从两侧映射同时删除") {
    val bimap = sample().removedValue("two")
    assertEquals(bimap.get(2), None)
    assertEquals(bimap.getKey("two"), None)
    assertEquals(bimap.forward, immutable.Map(1 -> "one"))
  }

  test("按不存在的值移除返回原实例") {
    val bimap = sample()
    assert((bimap) eq (bimap.removedValue("three")))
  }

  test("inverse 交换正向与反向映射") {
    val bimap = sample()
    assertEquals(bimap.inverse.forward, bimap.reverse)
    assertEquals(bimap.inverse.reverse, bimap.forward)
    assertEquals(bimap.inverse.get("two"), Some(2))
  }

  test("inverse 交换具体映射类型") {
    val bimap = BiMap(
      immutable.HashMap.empty[Int, String],
      immutable.TreeMap.empty[String, Int]
    ).updated(1, "one")

    assert(bimap.inverse.forward.isInstanceOf[immutable.TreeMap[?, ?]])
    assert(bimap.inverse.reverse.isInstanceOf[immutable.HashMap[?, ?]])
  }

  test("inverse 往返恢复两侧映射") {
    val bimap = sample()
    assertEquals(bimap.inverse.inverse.forward, bimap.forward)
    assertEquals(bimap.inverse.inverse.reverse, bimap.reverse)
  }

  test("修改不触碰原实例") {
    val bimap = sample()
    bimap.updated(3, "three")
    bimap.updated(1, "uno")
    bimap.removed(1)
    bimap.removedValue("two")
    bimap.inverse
    assertEquals(bimap.forward, immutable.Map(1 -> "one", 2 -> "two"))
    assertEquals(bimap.reverse, immutable.Map("one" -> 1, "two" -> 2))
  }

  test("混合操作保持两个方向互为逆映射") {
    var bimap = BiMap.empty[Int, String]
    bimap = bimap + (1 -> "one") + (2 -> "two")
    bimap = bimap.updated(3, "one")
    bimap = bimap.removed(2)
    bimap = bimap.removedValue("nope")
    bimap = bimap ++ List(4 -> "four", 4 -> "cuatro")
    bimap = bimap.updated(4, "four")
    assertEquals(bimap.reverse, bimap.forward.map(_.swap))
  }
}
