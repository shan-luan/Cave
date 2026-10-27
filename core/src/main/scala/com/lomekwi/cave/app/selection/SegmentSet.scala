package com.lomekwi.cave.app.selection

import com.lomekwi.cave.app.copy.Copyable
import com.lomekwi.cave.project.{Id, ProjectSession}
import com.lomekwi.cave.timeline.Timeline

import scala.collection.mutable

/**
 * 当前选中的一组片段。选中态不写回模型，只存在于界面层，
 * 因此这里持有的片段与轨道上的条目是同一批对象，选中与否不改变模型。
 */
class SegmentSet(@transient private val context: ProjectSession) extends mutable.LinkedHashSet[Id] with Serializable with Copyable {

  def timeline: Timeline = context.timeline

  override def copy(): Copyable = ???
}
