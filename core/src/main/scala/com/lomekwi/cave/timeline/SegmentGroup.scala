package com.lomekwi.cave.timeline

import java.io.Serializable

@SerialVersionUID(1L)
final class SegmentGroup(val segments: Set[Segment]) extends Iterable[Segment] with Serializable {
  def contains(segment: Segment): Boolean = segments.contains(segment)

  def add(segment: Segment): SegmentGroup = new SegmentGroup(segments + segment)

  def addAll(more: Iterable[Segment]): SegmentGroup = new SegmentGroup(segments ++ more)

  def remove(segment: Segment): SegmentGroup = new SegmentGroup(segments - segment)

  override def isEmpty: Boolean = segments.isEmpty

  override def iterator: Iterator[Segment] = segments.iterator

  override def equals(o: Any): Boolean = o match {
    case other: SegmentGroup => segments == other.segments
    case _ => false
  }

  override def hashCode(): Int = segments.hashCode()
}
