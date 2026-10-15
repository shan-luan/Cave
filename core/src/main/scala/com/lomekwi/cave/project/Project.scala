package com.lomekwi.cave.project

import com.lomekwi.cave.resource.Resource
import com.lomekwi.cave.timeline.Timeline
import com.lomekwi.cave.util.i18n.I18N.i18n

import java.io.File
import java.io.Serializable
import java.util.UUID

import scala.collection.mutable

@SerialVersionUID(3L)
final class Project(val name: String,
                    val timeline: Timeline,
                    val resources: mutable.LinkedHashMap[File, mutable.ArrayBuffer[Resource]],
                    val uuid: UUID) extends Serializable {

  def withName(name: String): Project = new Project(name, timeline, resources, uuid)

  def withTimeline(timeline: Timeline): Project = new Project(name, timeline, resources, uuid)
}

object Project {
  def create(): Project = new Project(
    i18n("未命名"),
    Timeline.empty,
    mutable.LinkedHashMap.empty[File, mutable.ArrayBuffer[Resource]],
    UUID.randomUUID())
}
