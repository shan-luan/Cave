package com.lomekwi.cave.app.selection

import com.lomekwi.cave.app.copy.{Copyable, PasteTemplate}
import com.lomekwi.cave.pipeline.Source
import com.lomekwi.cave.timeline.Timeline

import java.io.Serializable
import java.util

/**
 * 当前选中的一组源。选中态不写回模型，只存在于界面层，
 * 因此这里持有的源与轨道上的条目是同一批对象，选中与否不改变模型。
 */
class SourceSet(@transient private var timeline: Timeline) extends util.AbstractCollection[Source[?]] with Serializable with Copyable {
  private final val sources: util.Set[Source[?]] = new util.LinkedHashSet[Source[?]]()

  override def add(source: Source[?]): Boolean = {
    sources.add(source)
  }

  override def remove(o: Any): Boolean = {
    sources.remove(o)
  }

  override def clear(): Unit = {
    sources.clear()
  }

  override def contains(o: Any): Boolean = {
    sources.contains(o)
  }

  override def isEmpty: Boolean = {
    sources.isEmpty
  }

  override def iterator(): util.Iterator[Source[?]] = {
    sources.iterator()
  }

  override def size(): Int = {
    sources.size()
  }

  /** 该选中集描述的是哪条时间线。事件会跨项目投递，接收方靠它辨别归属。 */
  def getTimeline: Timeline = {
    timeline
  }

  /**
   * 抓一份剪贴板模板：源深拷贝，位置与组结构一并带走。
   * 位置必须随模板走，否则原对象被删除后粘贴就失去了落点依据。
   */
  override def copy(): Copyable = {
    PasteTemplate.of(this, timeline)
  }
}
