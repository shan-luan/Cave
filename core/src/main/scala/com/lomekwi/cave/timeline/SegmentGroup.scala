package com.lomekwi.cave.timeline

import com.lomekwi.cave.pipeline.Segment

import java.io.Serializable
import java.util

/**
 * 一组被同时操作（拖动/分割）的片段。组可以跨轨道，因此不归属于任何轨道，
 * 由 [[Timeline]] 统一登记。
 */
@SerialVersionUID(1L)
class SegmentGroup extends util.AbstractCollection[Segment[?]] with Serializable {
  private final val segments: util.Set[Segment[?]] = new util.LinkedHashSet[Segment[?]]()

  override def add(segment: Segment[?]): Boolean = {
    segments.add(segment)
  }

  override def remove(o: Any): Boolean = {
    segments.remove(o)
  }

  override def clear(): Unit = {
    segments.clear()
  }

  override def contains(o: Any): Boolean = {
    segments.contains(o)
  }

  override def isEmpty: Boolean = {
    segments.isEmpty
  }

  override def iterator(): util.Iterator[Segment[?]] = {
    segments.iterator()
  }

  override def size(): Int = {
    segments.size()
  }
}
