package com.lomekwi.cave.collection

import org.junit.jupiter.api.Assertions.{assertEquals, assertFalse, assertSame, assertThrows, assertTrue}
import org.junit.jupiter.api.Test

import scala.collection.immutable

class BiMapTest {

  private def sample() =
    BiMap(immutable.Map.empty[Int, String], immutable.Map.empty[String, Int])
      .updated(1, "one")
      .updated(2, "two")

  @Test
  def apply_acceptsEmptyMapsAndBuildsBothDirections(): Unit = {
    val bimap = sample()
    assertEquals(immutable.Map(1 -> "one", 2 -> "two"), bimap.forward)
    assertEquals(immutable.Map("one" -> 1, "two" -> 2), bimap.reverse)
  }

  @Test
  def apply_rejectsNonEmptyMaps(): Unit = {
    assertThrows(
      classOf[IllegalArgumentException],
      () => BiMap(immutable.Map(1 -> "one"), immutable.Map.empty[String, Int])
    )
    assertThrows(
      classOf[IllegalArgumentException],
      () => BiMap(immutable.Map.empty[Int, String], immutable.Map("one" -> 1))
    )
  }

  @Test
  def operations_preserveConcreteMapTypes(): Unit = {
    val bimap = BiMap(
      immutable.HashMap.empty[Int, String],
      immutable.TreeMap.empty[String, Int]
    ).updated(2, "two").updated(1, "one").updated(3, "three")

    assertTrue(bimap.forward.isInstanceOf[immutable.HashMap[?, ?]])
    assertTrue(bimap.reverse.isInstanceOf[immutable.TreeMap[?, ?]])
    assertEquals(List("one", "three", "two"), bimap.reverse.keys.toList)
  }

  @Test
  def get_looksUpBothDirections(): Unit = {
    val bimap = sample()
    assertEquals(Some("one"), bimap.get(1))
    assertEquals(Some(2), bimap.getKey("two"))
  }

  @Test
  def get_missingKeyOrValueReturnNone(): Unit = {
    val bimap = sample()
    assertEquals(None, bimap.get(3))
    assertEquals(None, bimap.getKey("three"))
  }

  @Test
  def contains_checksBothDirections(): Unit = {
    val bimap = sample()
    assertTrue(bimap.contains(1))
    assertFalse(bimap.contains(3))
    assertTrue(bimap.containsValue("two"))
    assertFalse(bimap.containsValue("four"))
  }

  @Test
  def size_isEmpty_reflectForwardMap(): Unit = {
    assertTrue(BiMap.empty[Int, String].isEmpty)
    assertEquals(0, BiMap.empty[Int, String].size)
    assertEquals(2, sample().size)
    assertFalse(sample().isEmpty)
  }

  @Test
  def updated_addsNewBindingToBothMaps(): Unit = {
    val bimap = sample().updated(3, "three")
    assertEquals(Some("three"), bimap.get(3))
    assertEquals(Some(3), bimap.getKey("three"))
    assertEquals(3, bimap.size)
  }

  @Test
  def updated_rebindsKeyAndEvictsOldValue(): Unit = {
    val bimap = sample().updated(1, "uno")
    assertEquals(Some("uno"), bimap.get(1))
    assertEquals(Some(1), bimap.getKey("uno"))
    assertEquals(None, bimap.getKey("one"))
    assertEquals(2, bimap.size)
  }

  @Test
  def updated_rebindsValueAndEvictsOldKey(): Unit = {
    val bimap = sample().updated(3, "one")
    assertEquals(Some("one"), bimap.get(3))
    assertEquals(Some(3), bimap.getKey("one"))
    assertEquals(None, bimap.get(1))
    assertEquals(2, bimap.size)
  }

  @Test
  def updated_evictsBothSidesWhenBothConflict(): Unit = {
    // 1 -> one 与 2 -> two，把 2 重绑到 one：1 与 two 都被驱逐
    val bimap = sample().updated(2, "one")
    assertEquals(immutable.Map(2 -> "one"), bimap.forward)
    assertEquals(immutable.Map("one" -> 2), bimap.reverse)
  }

  @Test
  def updated_existingBindingLeavesContentUnchanged(): Unit = {
    val bimap = sample()
    val reupdated = bimap.updated(1, "one")
    assertEquals(bimap.forward, reupdated.forward)
    assertEquals(bimap.reverse, reupdated.reverse)
  }

  @Test
  def plus_acceptsTuple(): Unit = {
    val bimap = sample() + (3 -> "three")
    assertEquals(Some(3), bimap.getKey("three"))
  }

  @Test
  def plusplus_foldsEntriesInOrder(): Unit = {
    val bimap = sample() ++ List(3 -> "three", 1 -> "uno")
    assertEquals(immutable.Map(1 -> "uno", 2 -> "two", 3 -> "three"), bimap.forward)
    assertEquals(immutable.Map("uno" -> 1, "two" -> 2, "three" -> 3), bimap.reverse)
  }

  @Test
  def removed_removesBindingFromBothMaps(): Unit = {
    val bimap = sample().removed(1)
    assertEquals(None, bimap.get(1))
    assertEquals(None, bimap.getKey("one"))
    assertEquals(immutable.Map(2 -> "two"), bimap.forward)
  }

  @Test
  def removed_missingKeyReturnsSameInstance(): Unit = {
    val bimap = sample()
    assertSame(bimap, bimap.removed(3))
    assertSame(bimap, bimap - 3)
  }

  @Test
  def removedValue_removesBindingFromBothMaps(): Unit = {
    val bimap = sample().removedValue("two")
    assertEquals(None, bimap.get(2))
    assertEquals(None, bimap.getKey("two"))
    assertEquals(immutable.Map(1 -> "one"), bimap.forward)
  }

  @Test
  def removedValue_missingValueReturnsSameInstance(): Unit = {
    val bimap = sample()
    assertSame(bimap, bimap.removedValue("three"))
  }

  @Test
  def inverse_swapsForwardAndReverse(): Unit = {
    val bimap = sample()
    assertEquals(bimap.reverse, bimap.inverse.forward)
    assertEquals(bimap.forward, bimap.inverse.reverse)
    assertEquals(Some(2), bimap.inverse.get("two"))
  }

  @Test
  def inverse_swapsConcreteMapTypes(): Unit = {
    val bimap = BiMap(
      immutable.HashMap.empty[Int, String],
      immutable.TreeMap.empty[String, Int]
    ).updated(1, "one")

    assertTrue(bimap.inverse.forward.isInstanceOf[immutable.TreeMap[?, ?]])
    assertTrue(bimap.inverse.reverse.isInstanceOf[immutable.HashMap[?, ?]])
  }

  @Test
  def inverse_roundTripRestoresBothMaps(): Unit = {
    val bimap = sample()
    assertEquals(bimap.forward, bimap.inverse.inverse.forward)
    assertEquals(bimap.reverse, bimap.inverse.inverse.reverse)
  }

  @Test
  def modifications_doNotTouchOriginalInstance(): Unit = {
    val bimap = sample()
    bimap.updated(3, "three")
    bimap.updated(1, "uno")
    bimap.removed(1)
    bimap.removedValue("two")
    bimap.inverse
    assertEquals(immutable.Map(1 -> "one", 2 -> "two"), bimap.forward)
    assertEquals(immutable.Map("one" -> 1, "two" -> 2), bimap.reverse)
  }

  @Test
  def mixedOperations_keepForwardAndReverseInverse(): Unit = {
    var bimap = BiMap.empty[Int, String]
    bimap = bimap + (1 -> "one") + (2 -> "two")
    bimap = bimap.updated(3, "one")
    bimap = bimap.removed(2)
    bimap = bimap.removedValue("nope")
    bimap = bimap ++ List(4 -> "four", 4 -> "cuatro")
    bimap = bimap.updated(4, "four")
    assertEquals(bimap.forward.map(_.swap), bimap.reverse)
  }
}
