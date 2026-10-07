package com.lomekwi.cave.pipeline

/**
 * 帧。并不局限于视频，是通用的数据容器。
 * 可能持有资源。应当被尽可能复用于 [[Source]]。
 */
abstract class Frame(final val trackIndex: Int, val segment: Segment) extends AutoCloseable {

  @volatile var timestamp: Long = 0L
  @volatile var closed: Boolean = false

  def this(trackIndex: Int) = {
    this(trackIndex, null)
  }

  def withTime(timestamp: Long): Frame = {
    this.timestamp = timestamp
    this
  }
  override def close(): Unit = {
    closed = true
  }
}
