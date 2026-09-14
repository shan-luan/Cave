package com.lomekwi.cave.timeline

import com.lomekwi.cave.app.copy.Copyable
import com.lomekwi.cave.app.selection.Selectable

import java.io.Serializable
import java.util.{AbstractCollection, Collections, HashMap, Iterator, LinkedHashSet, Map, Set}

import scala.jdk.CollectionConverters.*

/**
 * 当前选中的一组片段，实现 {@link Collection}{@code <Segment>}，
 */
@SerialVersionUID(1L)
class SegmentSet extends AbstractCollection[Segment] with Serializable with Selectable with Copyable {
  private final val segments: Set[Segment] = new LinkedHashSet[Segment]()
  @transient private var selected: Boolean = false

  override def add(segment: Segment): Boolean = {
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

  override def isEmpty(): Boolean = {
    segments.isEmpty
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
    var commonGroup: SegmentGroup = null
    var stopped = false
    for (seg <- segments.asScala if !stopped) {
      val g = seg.getGroup()
      if (g == null) {
        commonGroup = null
        stopped = true
      } else if (commonGroup == null) {
        commonGroup = g
      } else if (!g.eq(commonGroup)) {
        commonGroup = null
        stopped = true
      }
    }
    if (commonGroup != null) {
      return commonGroup.copy()
    }
    val set = new SegmentSet()
    val groupCopies: Map[SegmentGroup, SegmentGroup] = new HashMap[SegmentGroup, SegmentGroup]()
    for (seg <- segments.asScala) {
      val dup = seg.duplicate()
      dup.setTrack(seg.getTrack())
      val g = seg.getGroup()
      if (g != null) {
        var copyG = groupCopies.get(g)
        if (copyG == null) {
          copyG = new SegmentGroup()
          groupCopies.put(g, copyG)
        }
        copyG.add(dup)
      }
      set.add(dup)
    }
    set
  }
}
