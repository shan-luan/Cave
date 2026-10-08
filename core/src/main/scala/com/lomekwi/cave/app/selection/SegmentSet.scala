package com.lomekwi.cave.app.selection

import com.lomekwi.cave.app.copy.{Copyable, PasteTemplate}
import com.lomekwi.cave.pipeline.Segment
import com.lomekwi.cave.timeline.Timeline

import scala.collection.mutable

/**
 * 当前选中的一组片段。选中态不写回模型，只存在于界面层，
 * 因此这里持有的片段与轨道上的条目是同一批对象，选中与否不改变模型。
 */
class SegmentSet(@transient var timeline: Timeline) extends mutable.LinkedHashSet[Segment] with Serializable with Copyable {

  /** 抓一份剪贴板模板，片段深拷贝，位置与组结构一并带走。 */
  override def copy(): Copyable = {
    // 位置必须随模板走，否则原对象被删除后粘贴就失去了落点依据。
    PasteTemplate.of(this, timeline)
  }
}
