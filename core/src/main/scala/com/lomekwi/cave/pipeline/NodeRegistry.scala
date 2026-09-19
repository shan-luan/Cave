package com.lomekwi.cave.pipeline

import com.lomekwi.cave.pipeline.image.TransNode

import java.lang.{IllegalAccessException, InstantiationException}
import java.lang.reflect.{Constructor, InvocationTargetException, ParameterizedType, Type}
import java.util

import scala.jdk.CollectionConverters.*

/**
 * 通用节点注册表：注册任意 {@link Node} 子类，并按目标帧类型动态匹配可用的节点。
 *
 * <p>兼容性规则：节点是 {@link Filter} 时，看它泛型声明的目标帧类型是否
 * {@code isAssignableFrom} 源帧类型；非 Filter 的普通节点视为始终兼容。</p>
 */
class NodeRegistry {
  private final val entries: util.List[Class[? <: Node]] = new util.ArrayList[Class[? <: Node]]()

  register(classOf[TransNode])
  register(classOf[NodeGraphFilter])

  def register(nodeClass: Class[? <: Node]): Unit = {
    entries.add(nodeClass)
  }

  /** 已注册节点的数量（不做帧类型过滤）。 */
  def getCount: Int = {
    entries.size()
  }

  /** 按注册顺序创建第 index 个节点（不做帧类型过滤）。 */
  def create(index: Int): Node = {
    NodeRegistry.create(entries.get(index))
  }

  def getCompatibleCount(source: Source[?]): Int = {
    val frameType: Class[?] = source.getFrameType
    var count = 0
    for (nodeClass <- entries.asScala) {
      if (NodeRegistry.isCompatible(nodeClass, frameType)) count += 1
    }
    count
  }

  /**
   * 创建第 index 个兼容节点。返回类型按 Filter 使用方约定——非 Filter 节点
   * 目前没有消费方，Inspector 只会把结果加入 filter 链。
   */
  def createCompatible(source: Source[?], index: Int): Node = {
    val frameType: Class[?] = source.getFrameType
    entries.asScala.iterator
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
      true // 非 Filter 节点始终兼容
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
