package com.lomekwi.cave.timeline

import java.io.Serializable

@SerialVersionUID(1L)
case class SegmentGroup(segments: Set[Segment]) extends Iterable[Segment] with Serializable {
  def contains(segment: Segment): Boolean = segments.contains(segment)

  def add(segment: Segment): SegmentGroup = SegmentGroup(segments + segment)

  def addAll(more: Iterable[Segment]): SegmentGroup = SegmentGroup(segments ++ more)

  def remove(segment: Segment): SegmentGroup = SegmentGroup(segments - segment)

  override def isEmpty: Boolean = segments.isEmpty

  override def iterator: Iterator[Segment] = segments.iterator
}
