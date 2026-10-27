package com.lomekwi.cave.timeline

import com.lomekwi.cave.project.Id

import java.io.Serializable

@SerialVersionUID(1L)
case class SegmentGroup(segments: Set[Id]) extends Iterable[Id] with Serializable {
  def contains(id: Id): Boolean = segments.contains(id)

  def add(id: Id): SegmentGroup = SegmentGroup(segments + id)

  def addAll(more: Iterable[Id]): SegmentGroup = SegmentGroup(segments ++ more)

  def remove(id: Id): SegmentGroup = SegmentGroup(segments - id)

  override def isEmpty: Boolean = segments.isEmpty

  override def iterator: Iterator[Id] = segments.iterator
}
