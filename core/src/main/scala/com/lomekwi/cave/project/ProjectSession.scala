package com.lomekwi.cave.project

import com.badlogic.gdx.Gdx
import com.google.common.eventbus.{EventBus, Subscribe}
import com.lomekwi.cave.app.App
import com.lomekwi.cave.pipeline.audio.AudioFrameSink
import com.lomekwi.cave.playback.{Playhead, TrackWorker}
import com.lomekwi.cave.timeline.{Content, Interval, Segment, SegmentFactory, Timeline, Track}
import com.lomekwi.cave.util.legacy_i18n.I18N.i18n

import java.nio.file.Path
import scala.collection.mutable

final class ProjectSession(initial: Project) {
  var project: Project = initial
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
      project = project.withTimeline(next)
      for (i <- project.timeline.tracks.indices) {
        worker(i).onTrackChanged()
      }
    }
  }
  @Deprecated//过时的设计。改为一个copy()方法就行了。
  def record(): ProjectSession.Recording = {
    val before = project.timeline
    () => endRecord(before)
  }
  @Deprecated
  private def endRecord(before: Timeline): Unit = {
    val after = project.timeline
    if (after ne before) {
      touch()
      undo.push(UndoManager.TimelineEdit(before, after))
    }
  }

  def worker(index: Int): TrackWorker = synchronized {
    workers.getOrElseUpdate(index, new TrackWorker(this, index))
  }

  def tryAdd(track: Track, segment: Content, interval: Interval, origin: Long): Long = {
    val (next, shift) = timeline.tryAdd(track, segment, interval, origin)
    applyTimeline(next)
    shift
  }

  def remove(segment: Segment): Unit = applyTimeline(timeline.remove(segment))

  def remove(segments: Iterable[Segment]): Unit = applyTimeline(timeline.remove(segments))

  def split(track: Track, time: Long): Unit = applyTimeline(timeline.split(track, time))

  def setStart(segments: Iterable[Segment], deltaTime: Long): Long = {
    val (next, applied) = timeline.setStart(segments, deltaTime)
    applyTimeline(next)
    applied
  }

  def setEnd(segments: Iterable[Segment], deltaTime: Long): Long = {
    val (next, applied) = timeline.setEnd(segments, deltaTime)
    applyTimeline(next)
    applied
  }

  def moveTime(segments: Iterable[Segment], deltaTime: Long): Long = {
    val (next, applied) = timeline.moveTime(segments, deltaTime)
    applyTimeline(next)
    applied
  }

  def moveTrack(segments: Iterable[Segment], deltaTrack: Int): Int = {
    val (next, applied) = timeline.moveTrack(segments, deltaTrack)
    applyTimeline(next)
    applied
  }

  @Subscribe
  def onProjectFronted(event: ProjectFrontedEvent): Unit = {
    if (!active) {
      active = true
      Gdx.app.log("Project", i18n("项目 [") + project.name + i18n("] 激活，开始轨道循环"))
      update()
    }
  }

  @Subscribe
  def onProjectBackgrounded(event: ProjectBackgroundedEvent): Unit = {
    if (active) {
      active = false
      Gdx.app.log("Project", i18n("项目 [") + project.name + i18n("] 后台化，停止轨道循环"))
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
