package com.lomekwi.cave.timeline

import com.lomekwi.cave.app.copy.Copyable
import com.lomekwi.cave.app.selection.Selectable

import java.io.Serializable
import java.util
import java.util.{Collections}

import scala.jdk.CollectionConverters.*

/**
 * 当前选中的一组片段，实现 {@link Collection}{@code <Segment>}，
 */
@SerialVersionUID(1L)
class SegmentSet extends util.AbstractCollection[Segment] with Serializable with Selectable with Copyable {
  private final val segments: util.Set[Segment] = new util.LinkedHashSet[Segment]()
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

  override def isEmpty: Boolean = {
    segments.isEmpty
  }

  override def iterator(): util.Iterator[Segment] = {
    segments.iterator()
  }

  override def size(): Int = {
    segments.size()
  }

  def getSegments: util.Set[Segment] = {
    Collections.unmodifiableSet(segments)
  }

  override def isSelected: Boolean = {
    selected
  }

  override def setSelected(selected: Boolean): Unit = {
    this.selected = selected
    for (seg <- segments.asScala) {
      seg.setSelected(selected)
    }
  }

  override def copy(): Copyable = {
    val groups = segments.asScala.toSeq.map(seg => Option(seg.getGroup))
    val commonGroup = groups.headOption.flatten.filter(g => groups.forall(_.contains(g)))

    commonGroup.map(g => g.copy()).getOrElse {
      val set = new SegmentSet()
      val groupCopies: util.Map[SegmentGroup, SegmentGroup] = new util.HashMap[SegmentGroup, SegmentGroup]()
      for (seg <- segments.asScala) {
        val dup = seg.duplicate()
        dup.setTrack(seg.getTrack)
        val g = seg.getGroup
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
}
