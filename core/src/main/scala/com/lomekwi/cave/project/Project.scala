package com.lomekwi.cave.project

import com.lomekwi.cave.util.i18n.I18N.i18n

import com.badlogic.gdx.Gdx
import com.google.common.eventbus.EventBus
import com.google.common.eventbus.Subscribe
import com.lomekwi.cave.pipeline.audio.AudioFrameSink
import com.lomekwi.cave.resource.Resource
import com.lomekwi.cave.timeline.SourceFactory
import com.lomekwi.cave.timeline.Timeline
import com.lomekwi.cave.timeline.UndoManager
import com.lomekwi.cave.timeline.playback.Playhead
import com.lomekwi.cave.app.App

import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.Multimap

import java.io.File
import java.io.ObjectInputStream
import java.io.Serializable
import java.nio.file.Path
import java.util.UUID

import scala.compiletime.uninitialized
import scala.jdk.CollectionConverters.*

@SerialVersionUID(2L)
class Project protected[project] extends Serializable with AutoCloseable {
  @transient protected[project] var savePath: Path = uninitialized
  var timeline: Timeline = uninitialized
  @transient var playhead: Playhead = uninitialized
  final val resources: Multimap[File, Resource] = ArrayListMultimap.create[File, Resource]()
  final val sourceFactory: SourceFactory = new SourceFactory(this)
  @transient var projEventBus: EventBus = uninitialized
  @transient var undoManager: UndoManager = uninitialized
  var name: String = uninitialized
  private final val uuid: UUID = UUID.randomUUID()
  var savedVersion: Long = 0
  @transient var currentVersion: Long = 0

  @transient private var isActive: Boolean = false

  App.appEventBus.register(this)
  name = i18n("未命名")
  projEventBus = new EventBus(uuid.toString)
  undoManager = new UndoManager(this)
  projEventBus.register(new AudioFrameSink(this))
  projEventBus.register(this)
  timeline = new Timeline(this)
  playhead = new Playhead(projEventBus)

  def update(): Unit = {
    if (isActive) {
      for (track <- timeline.getTracks.asScala) {
        if (track.getWorker.getFuture == null || track.getWorker.getFuture.isDone) {
          val future = App.workerExecutor.submit(track.getWorker)
          track.getWorker.setFuture(future)
        }
      }
    }
  }

  @Subscribe
  def onProjectFronted(event: ProjectFrontedEvent): Unit = {
    if (!isActive) {
      isActive = true
      Gdx.app.log("Project", "项目 [" + name + "] 激活，开始轨道循环")
      update()
    }
  }

  @Subscribe
  def onProjectBackgrounded(event: ProjectBackgroundedEvent): Unit = {
    if (isActive) {
      isActive = false
      Gdx.app.log("Project", "项目 [" + name + "] 后台化，停止轨道循环")
      stopTrackLoops()
    }
  }

  private def stopTrackLoops(): Unit = {
    for (track <- timeline.getTracks.asScala) {
      val future = track.getWorker.getFuture
      if (future != null) {
        future.cancel(true)
        track.getWorker.setFuture(null)
      }
    }
  }


  override def close(): Unit = {
    projEventBus.post(ProjectBackgroundedEvent)
    App.appEventBus.unregister(this)
    isActive = false
    stopTrackLoops()
    resources.values().forEach((resource: Resource) => {
      try {
        resource.close()
      } catch {
        case e: Exception =>
          throw new RuntimeException(e)
      }
    })
  }

  private def readObject(in: ObjectInputStream): Unit = {
    in.defaultReadObject()
    currentVersion = savedVersion
    sourceFactory.setProject(this)
    App.appEventBus.register(this)
    projEventBus = new EventBus(uuid.toString)
    projEventBus.register(new AudioFrameSink(this))
    projEventBus.register(this)
    playhead = new Playhead(projEventBus)
    undoManager = new UndoManager(this)
    isActive = false
  }

  def getSavePath: Path = {
    savePath
  }

  private def writeObject(out: java.io.ObjectOutputStream): Unit = {
    savedVersion = currentVersion
    out.defaultWriteObject()
  }

  def isDirty: Boolean = {
    currentVersion != savedVersion
  }
}
