package com.lomekwi.cave.project

import com.badlogic.gdx.Gdx
import com.google.common.eventbus.{EventBus, Subscribe}
import com.lomekwi.cave.app.App
import com.lomekwi.cave.pipeline.audio.AudioFrameSink
import com.lomekwi.cave.playback.{Playhead, TrackWorker}
import com.lomekwi.cave.resource.Resource
import com.lomekwi.cave.timeline.{Content, Interval, Segment, SegmentFactory, Timeline, Track}
import com.lomekwi.cave.util.i18n.*
import com.lomekwi.cave.util.i18n.BasicLang.*
import com.lomekwi.cave.util.i18n.TransTargetType.given

import java.io.File
import java.nio.file.Path
import scala.collection.mutable

final class ProjectSession(initial: Project) {
  var project: Project = initial
  val idGenerator: IdGenerator = IdGenerator(initial.maxId)
  given idSource: IdSource = () => idGenerator.genId
  val bus: EventBus = new EventBus(initial.uuid.toString)
  val sourceFactory: SegmentFactory = new SegmentFactory(this)
  val undo: UndoManager = new UndoManager(this)
  val playhead: Playhead = new Playhead(bus)
  var active: Boolean = false
  var version: Long = 0L
  private var savedVersion: Long = 0L
  var savePath: Option[Path] = None

  private val workers: mutable.HashMap[Int, TrackWorker] = mutable.HashMap.empty

  bus.register(new AudioFrameSink(this))
  bus.register(this)
  App.appEventBus.register(this)

  def timeline: Timeline = project.timeline

  def isDirty: Boolean = version != savedVersion

  def bumpVersion(delta: Long): Unit = {
    val wasDirty = isDirty
    version += delta
    if (wasDirty != isDirty) {
      bus.post(ProjectDirtyChangedEvent)
    }
  }

  def touch(): Unit = bumpVersion(1)

  def markSaved(): Unit = {
    savedVersion = version
  }

  def applyTimeline(next: Timeline): Unit = {
    if (next ne project.timeline) {
      applyProject(project.withTimeline(next))
    }
  }

  def applyProject(next: Project): Unit = {
    val previousResources = project.resources
    project = next
    for (i <- project.timeline.tracks.indices) {
      worker(i).onTrackChanged()
    }
    postResourcesChanged(previousResources, project.resources)
  }

  def applyResources(next: Map[File, Vector[Resource]]): Unit = {
    undo.push(project)
    val previousResources = project.resources
    project = project.withResources(next)
    touch()
    postResourcesChanged(previousResources, next)
  }

  private def postResourcesChanged(before: Map[File, Vector[Resource]], after: Map[File, Vector[Resource]]): Unit = {
    if (after ne before) {
      val added = after.keySet.diff(before.keySet)
      val removed = before.keySet.diff(after.keySet)
      if (added.nonEmpty || removed.nonEmpty) {
        bus.post(ProjectResourcesChangedEvent(added, removed))
      }
    }
  }

  @deprecated("过时的设计。改为一个copy()方法就行了。")
  def record(): ProjectSession.Recording = {
    val before = project
    () => endRecord(before)
  }
  @deprecated
  private def endRecord(before: Project): Unit = {
    if (project ne before) {
      touch()
      undo.push(before)
    }
  }

  def worker(index: Int): TrackWorker = synchronized {
    workers.getOrElseUpdate(index, new TrackWorker(this, index))
  }

  def add(track: Track, segment: Content, interval: Interval, origin: Long): Timeline = {
    val next = timeline.add(track.index, segment, interval, origin)
    applyTimeline(next)
    next
  }

  def remove(id: Id): Unit = applyTimeline(timeline.remove(id))

  def remove(ids: Iterable[Id]): Unit = applyTimeline(timeline.remove(ids))

  def split(track: Track, time: Long): Unit = applyTimeline(timeline.split(track, time))

  def setStart(ids: Iterable[Id], deltaTime: Long): Long = {
    val (next, applied) = timeline.setStart(ids, deltaTime)
    applyTimeline(next)
    applied
  }

  def setEnd(ids: Iterable[Id], deltaTime: Long): Long = {
    val (next, applied) = timeline.setEnd(ids, deltaTime)
    applyTimeline(next)
    applied
  }

  def moveTime(ids: Iterable[Id], deltaTime: Long): Long = {
    val (next, applied) = timeline.moveTime(ids, deltaTime)
    applyTimeline(next)
    applied
  }

  def moveTrack(ids: Iterable[Id], deltaTrack: Int): Int = {
    val (next, applied) = timeline.moveTrack(ids, deltaTrack)
    applyTimeline(next)
    applied
  }

  @Subscribe
  def onProjectFronted(event: ProjectFrontedEvent): Unit = {
    if (!active) {
      active = true
      Gdx.app.log("Project", ("项目 [".in(Zh) | "Project [".in(En)).str() + project.name + ("] 激活，开始轨道循环".in(Zh) | "] activated, starting track loop".in(En)).str())
      update()
    }
  }

  @Subscribe
  def onProjectBackgrounded(event: ProjectBackgroundedEvent): Unit = {
    if (active) {
      active = false
      Gdx.app.log("Project", ("项目 [".in(Zh) | "Project [".in(En)).str() + project.name + ("] 后台化，停止轨道循环".in(Zh) | "] backgrounded, stopping track loop".in(En)).str())
      stopTrackLoops()
    }
  }

  def update(): Unit = {
    if (active) {
      for (track <- project.timeline.getTracks) {
        val w = worker(track.index)
        if (w.future == null || w.future.isDone) {
          w.future = App.workerExecutor.submit(w)
        }
      }
    }
  }

  private def stopTrackLoops(): Unit = {
    for (track <- project.timeline.getTracks) {
      val w = worker(track.index)
      val future = w.future
      if (future != null) {
        future.cancel(true)
        w.future = null
      }
    }
  }

  def close(): Unit = {
    bus.post(ProjectBackgroundedEvent)
    App.appEventBus.unregister(this)
    active = false
    stopTrackLoops()
    for (buffer <- project.resources.values; resource <- buffer) {
      try {
        resource.close()
      } catch {
        case e: Exception =>
          throw new RuntimeException(e)
      }
    }
  }
}

object ProjectSession {
  trait Recording extends AutoCloseable {
    override def close(): Unit
  }
}
