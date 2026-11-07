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

  def get(key: K): Option[V] = forward.get(key)

  def getKey(value: V): Option[K] = reverse.get(value)

  def contains(key: K): Boolean = forward.contains(key)

  def containsValue(value: V): Boolean = reverse.contains(value)

  def size: Int = forward.size

  def isEmpty: Boolean = forward.isEmpty

  /** 交换正反方向得到反向视图。 */
  def inverse: BiMap[V, K, V2KM, K2VM] = new BiMap(reverse, forward)

  /** 绑定 key 与 value。 */
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

  /** 按 key 解除绑定。 */
  def removed(key: K): BiMap[K, V, K2VM, V2KM] = forward.get(key) match {
    case Some(value) => new BiMap(forward.removed(key).asInstanceOf[K2VM], reverse.removed(value).asInstanceOf[V2KM])
    case None => this
  }

  def -(key: K): BiMap[K, V, K2VM, V2KM] = removed(key)

  /** 按 value 解除绑定。 */
  def removedValue(value: V): BiMap[K, V, K2VM, V2KM] = reverse.get(value) match {
    case Some(key) => new BiMap(forward.removed(key).asInstanceOf[K2VM], reverse.removed(value).asInstanceOf[V2KM])
    case None => this
  }

  override def toString: String = s"BiMap($forward, $reverse)"
}

object BiMap {

  /**
   * 以一对空 Map 构造。
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
