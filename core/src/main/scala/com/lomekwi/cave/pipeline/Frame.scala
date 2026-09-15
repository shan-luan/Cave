package com.lomekwi.cave.pipeline

import com.lomekwi.cave.timeline.Track

import java.io.Serializable

@SerialVersionUID(1L)
abstract class Frame(final val track: Track, private val source: Source[?]) extends AutoCloseable with Serializable {

  @volatile var timestamp: Long = 0L
  @volatile private var closed: Boolean = false

  def this(track: Track) = {
    this(track, null)
  }

  def getSource: Source[?] = source

  def withTime(timestamp: Long): Frame = {
    this.timestamp = timestamp
    this
  }
  override def close(): Unit = {
    closed = true
  }

  def isClosed: Boolean = closed
}
