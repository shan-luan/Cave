package com.lomekwi.cave.pipeline

import com.lomekwi.cave.project.Id

/**
 * 帧。并不局限于视频，是通用的数据容器。
 * 可能持有资源。应当被尽可能复用于 [[Source]]。
 */
@deprecated("此基类应该被废弃。参见此处的TODO")
//TODO:这个类的存在让“在片段上的”和“节点图中的”数据在类型系统上不兼容。作为抽象扩展点，应该被移除。其实现不应该继承什么通用的东西。此类的职责应该被用组合方式替代。
abstract class Frame(final val trackIndex: Int, val segmentId: Id) extends AutoCloseable {

  @volatile var timestamp: Long = 0L
  @volatile var closed: Boolean = false

  def this(trackIndex: Int) = {
    this(trackIndex, Id.noId())
  }

  def withTime(timestamp: Long): Frame = {
    this.timestamp = timestamp
    this
  }
  override def close(): Unit = {
    closed = true
  }
}
