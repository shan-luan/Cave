package com.lomekwi.cave.timeline

import com.lomekwi.cave.app.copy.Copyable
import com.lomekwi.cave.app.selection.Selectable

import java.io.Serializable
import java.util.{AbstractCollection, Collections, Iterator, LinkedHashSet, Set}

import scala.jdk.CollectionConverters.*

/**
 * 一组被同时操作（选择/拖动/分割）的片段，实现 {@link Collection}{@code <Segment>}，
 */
@SerialVersionUID(1L)
class SegmentGroup extends AbstractCollection[Segment] with Serializable with Selectable with Copyable {
  private final val segments: Set[Segment] = new LinkedHashSet[Segment]()
  @transient private var selected: Boolean = false

  override def add(segment: Segment): Boolean = {
    if (segments.add(segment)) {
      segment.setGroup(this)
      return true
    }
    false
  }

  override def remove(o: Any): Boolean = o match {
    case seg: Segment if segments.remove(seg) =>
      seg.setGroup(null)
      true
    case _ => false
  }

  override def clear(): Unit = {
    for (s <- segments.asScala) s.setGroup(null)
    segments.clear()
  }

  override def contains(o: Any): Boolean = {
    segments.contains(o)
  }

  override def isEmpty(): Boolean = {
    segments.isEmpty()
  }

  override def iterator(): Iterator[Segment] = {
    segments.iterator()
  }

  override def size(): Int = {
    segments.size()
  }

  def getSegments(): Set[Segment] = {
    Collections.unmodifiableSet(segments)
  }

  override def isSelected(): Boolean = {
    selected
  }

  override def setSelected(selected: Boolean): Unit = {
    this.selected = selected
    for (seg <- segments.asScala) {
      seg.setSelected(selected)
    }
  }

  override def copy(): Copyable = {
    val newGroup = new SegmentGroup()
    for (seg <- segments.asScala) {
      val dup = seg.duplicate()
      dup.setTrack(seg.getTrack())
      newGroup.add(dup)
    }
    newGroup
  }
}
