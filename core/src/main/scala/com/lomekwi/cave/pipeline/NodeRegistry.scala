package com.lomekwi.cave.pipeline

import com.lomekwi.cave.pipeline.image.TransNode
import com.lomekwi.cave.pipeline.num.{AddNode, DivNode, MulNode, RandomNode, SubNode}

import java.lang.{IllegalAccessException, InstantiationException}
import java.lang.reflect.{Constructor, InvocationTargetException, ParameterizedType, Type}

import scala.collection.mutable

/**
 * 通用节点注册表，注册任意 {@link Node} 子类，并按目标帧类型动态匹配可用的节点。
 *
 * <p>兼容性规则，节点是 {@link Filter} 时，看它泛型声明的目标帧类型是否
 * {@code isAssignableFrom} 源帧类型；非 Filter 的图内节点不参与帧类型匹配，
 * 始终视为不兼容，因为兼容节点只会被挂到源的滤镜链上。</p>
 */
class NodeRegistry {
  private final val entries: mutable.ArrayBuffer[Class[? <: Node]] = mutable.ArrayBuffer.empty[Class[? <: Node]]

  register(classOf[TransNode])
  register(classOf[NodeGraphFilter])
  register(classOf[RandomNode])
  register(classOf[AddNode])
  register(classOf[SubNode])
  register(classOf[MulNode])
  register(classOf[DivNode])

  def register(nodeClass: Class[? <: Node]): Unit = {
    entries += nodeClass
  }

  /** 已注册节点的数量（不做帧类型过滤）。 */
  def getCount: Int = {
    entries.size
  }

  /** 按注册顺序创建第 index 个节点（不做帧类型过滤）。 */
  def create(index: Int): Node = {
    NodeRegistry.create(entries(index))
  }

  def getCompatibleCount(source: Source[?]): Int = {
    val frameType: Class[?] = source.getType
    var count = 0
    for (nodeClass <- entries) {
      if (NodeRegistry.isCompatible(nodeClass, frameType)) count += 1
    }
    count
  }

  /**
   * 创建第 index 个兼容节点。只会返回可挂到源滤镜链上的 {@link Filter} 节点，
   * 非 Filter 的图内节点不参与匹配。
   */
  def createCompatible(source: Source[?], index: Int): Node = {
    val frameType: Class[?] = source.getType
    entries.iterator
      .filter(nodeClass => NodeRegistry.isCompatible(nodeClass, frameType))
      .zipWithIndex
      .collectFirst { case (nodeClass, i) if i == index => NodeRegistry.create(nodeClass) }
      .orNull
  }
}

object NodeRegistry {
  private def create(nodeClass: Class[? <: Node]): Node = {
    try {
      val ctor: Constructor[?] = nodeClass.getConstructor()
      val node: Node = ctor.newInstance().asInstanceOf[Node]
      node
    } catch {
      case e: NoSuchMethodException =>
        throw new IllegalArgumentException(nodeClass.getName + " 缺少无参构造器", e)
      case e @ (_: InvocationTargetException | _: InstantiationException | _: IllegalAccessException) =>
        throw new RuntimeException("创建节点 " + nodeClass.getName + " 失败", e)
    }
  }

  private def isCompatible(nodeClass: Class[? <: Node], frameType: Class[?]): Boolean = {
    if (!classOf[Filter[?]].isAssignableFrom(nodeClass)) {
      false
    } else {
      val asFilter: Class[? <: Filter[?]] = nodeClass.asSubclass(classOf[Filter[?]])
      targetTypeOf(asFilter).isAssignableFrom(frameType)
    }
  }

  private def targetTypeOf(filterClass: Class[? <: Filter[?]]): Class[?] = {
    val arg: Option[Type] = filterClass.getGenericSuperclass match {
      case pt: ParameterizedType => Some(pt.getActualTypeArguments()(0))
      case _ => None
    }
    arg.collect { case clazz: Class[?] => clazz }
      .getOrElse(throw new IllegalArgumentException("无法从 " + filterClass.getName + " 推断目标类型"))
  }
}
