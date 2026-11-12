package com.lomekwi.cave.app.selection

import com.lomekwi.cave.app.copy.{Copyable, PasteTemplate}
import com.lomekwi.cave.project.{Id, ProjectSession}
import com.lomekwi.cave.timeline.Timeline

import scala.collection.mutable

/**
 * 当前选中的一组片段。
 */
class SegmentSet(@transient private val session: ProjectSession) extends mutable.LinkedHashSet[Id] with Serializable with Copyable {

  def timeline: Timeline = session.timeline

  override def copy(): Copyable = {
    val segments = iterator.flatMap(id => timeline.segment(id)).toVector
    PasteTemplate.of(segments, timeline)(using session.idSource)
  }
}
