package com.lomekwi.cave.collection

import scala.collection.immutable

import java.io.Serializable

/**
 * 不可变双向映射，[[forward]] 与 [[reverse]] 互为逆映射。
 *
 * @tparam K    键类型
 * @tparam V    值类型
 * @tparam K2VM 正向视图的实现类型
 * @tparam V2KM 反向视图的实现类型
 */
@SerialVersionUID(1L)
final class BiMap[K, V, K2VM <: immutable.Map[K, V], V2KM <: immutable.Map[V, K]] private (
    val forward: K2VM,
    val reverse: V2KM
) extends Serializable {

  // 互逆性无需内容校验。初始两个 Map 为空，空集的逆映射仍是空集，
  // 所有修改又构造性地维持一一对应，归纳可知任意状态下互逆。
  // K2VM 与 V2KM 须遵循 Scala 集合约定，updated 与 removed 返回同类型实例
  // （标准库实现均满足），各方法据此恢复具体类型。

  def get(key: K): Option[V] = forward.get(key)

  def getKey(value: V): Option[K] = reverse.get(value)

  def contains(key: K): Boolean = forward.contains(key)

  def containsValue(value: V): Boolean = reverse.contains(value)

  def size: Int = forward.size

  def isEmpty: Boolean = forward.isEmpty

  /** 交换正反方向得到反向视图，O(1)。 */
  def inverse: BiMap[V, K, V2KM, K2VM] = new BiMap(reverse, forward)

  /**
   * 绑定 key 与 value。若 key 原本绑定其他 value，或 value 原本绑定其他 key，
   * 冲突的旧绑定会被驱逐，保持一一对应。
   */
  def updated(key: K, value: V): BiMap[K, V, K2VM, V2KM] = {
    val rawForward = reverse.get(value) match {
      case Some(oldKey) if oldKey != key => forward.updated(key, value).removed(oldKey)
      case _ => forward.updated(key, value)
    }
    val rawReverse = forward.get(key) match {
      case Some(oldValue) if oldValue != value => reverse.updated(value, key).removed(oldValue)
      case _ => reverse.updated(value, key)
    }
    new BiMap(rawForward.asInstanceOf[K2VM], rawReverse.asInstanceOf[V2KM])
  }

  def +(entry: (K, V)): BiMap[K, V, K2VM, V2KM] = updated(entry._1, entry._2)

  def ++(entries: IterableOnce[(K, V)]): BiMap[K, V, K2VM, V2KM] =
    entries.iterator.foldLeft(this)(_ + _)

  /** 按 key 解除绑定，key 不存在时返回自身。 */
  def removed(key: K): BiMap[K, V, K2VM, V2KM] = forward.get(key) match {
    case Some(value) => new BiMap(forward.removed(key).asInstanceOf[K2VM], reverse.removed(value).asInstanceOf[V2KM])
    case None => this
  }

  def -(key: K): BiMap[K, V, K2VM, V2KM] = removed(key)

  /** 按 value 解除绑定，value 不存在时返回自身。 */
  def removedValue(value: V): BiMap[K, V, K2VM, V2KM] = reverse.get(value) match {
    case Some(key) => new BiMap(forward.removed(key).asInstanceOf[K2VM], reverse.removed(value).asInstanceOf[V2KM])
    case None => this
  }

  override def toString: String = s"BiMap($forward, $reverse)"
}

object BiMap {

  /**
   * 以一对空 Map 构造，两者的具体实现类型决定正反视图的类型与性能特征。
   */
  def apply[K, V, K2VM <: immutable.Map[K, V], V2KM <: immutable.Map[V, K]](
      emptyForward: K2VM,
      emptyReverse: V2KM
  ): BiMap[K, V, K2VM, V2KM] = {
    require(emptyForward.isEmpty, "forward map must be empty")
    require(emptyReverse.isEmpty, "reverse map must be empty")
    new BiMap(emptyForward, emptyReverse)
  }

  def empty[K, V]: BiMap[K, V, immutable.Map[K, V], immutable.Map[V, K]] =
    new BiMap(immutable.Map.empty, immutable.Map.empty)
}
