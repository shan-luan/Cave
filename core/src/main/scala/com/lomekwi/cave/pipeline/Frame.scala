package com.lomekwi.cave.pipeline

import java.io.Serializable

@SerialVersionUID(1L)
abstract class Frame(final val trackIndex: Int, private val segment: Segment[?]) extends AutoCloseable with Serializable {

  @volatile var timestamp: Long = 0L
  @volatile private var closed: Boolean = false

  def this(trackIndex: Int) = {
    this(trackIndex, null)
  }

  def getSegment: Segment[?] = segment

  def withTime(timestamp: Long): Frame = {
    this.timestamp = timestamp
    this
  }
  override def close(): Unit = {
    closed = true
  }

  def isClosed: Boolean = closed
}
