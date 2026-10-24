package com.lomekwi.cave.project

import com.lomekwi.cave.playback.Playhead
import com.lomekwi.cave.playback.TrackWorker
import com.lomekwi.cave.resource.Resource
import com.lomekwi.cave.timeline.{TestTimeline, Timeline}

import java.io.File
import java.util.UUID

class TestProject() {
  val project: Project = new Project(
    "test",
    Timeline.empty,
    Map.empty[File, Vector[Resource]],
    UUID.randomUUID())
  val context: ProjectSession = new ProjectSession(project)
  val timeline: TestTimeline = new TestTimeline(context)

  def playhead: Playhead = context.playhead

  def undo: UndoManager = context.undo

  def worker(index: Int): TrackWorker = context.worker(index)

  def close(): Unit = context.close()
}
