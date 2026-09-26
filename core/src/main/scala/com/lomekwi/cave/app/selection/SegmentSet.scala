package com.lomekwi.cave.app.selection

import com.lomekwi.cave.app.copy.{Copyable, PasteTemplate}
import com.lomekwi.cave.pipeline.Segment
import com.lomekwi.cave.timeline.Timeline

import java.io.Serializable
import java.util

/**
 * 当前选中的一组片段。选中态不写回模型，只存在于界面层，
 * 因此这里持有的片段与轨道上的条目是同一批对象，选中与否不改变模型。
 */
class SegmentSet(@transient private var timeline: Timeline) extends util.AbstractCollection[Segment[?]] with Serializable with Copyable {
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

  /** 该选中集描述的是哪条时间线。事件会跨项目投递，接收方靠它辨别归属。 */
  def getTimeline: Timeline = {
    timeline
  }

  /**
   * 抓一份剪贴板模板，片段深拷贝，位置与组结构一并带走。
   * 位置必须随模板走，否则原对象被删除后粘贴就失去了落点依据。
   */
  override def copy(): Copyable = {
    PasteTemplate.of(this, timeline)
  }
}
