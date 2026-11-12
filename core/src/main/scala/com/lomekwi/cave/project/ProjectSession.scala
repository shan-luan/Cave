package com.lomekwi.cave.project

import com.badlogic.gdx.Gdx
import com.google.common.eventbus.{EventBus, Subscribe}
import com.lomekwi.cave.app.App
import com.lomekwi.cave.pipeline.NodeGraphFilter
import com.lomekwi.cave.pipeline.audio.AudioFrameSink
import com.lomekwi.cave.playback.{Playhead, TrackWorker}
import com.lomekwi.cave.resource.Resource
import com.lomekwi.cave.timeline.{Segment, SegmentFactory, Timeline}
import com.lomekwi.cave.util.i18n.*
import com.lomekwi.cave.util.i18n.BasicLang.*

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
      applyProject(_.withTimeline(next))
    }
  }

  def updateSegment(id: Id)(f: Segment => Segment): Unit = {
    applyTimeline(timeline.updateSegment(id)(f))
    touch()
  }

  def commitGraph(path: Vector[NodeGraphFilter.Owner], target: NodeGraphFilter): Unit = {
    if (path.isEmpty) return
    val before = project
    path.head match {
      case NodeGraphFilter.InChain(segmentId, filterId) =>
        timeline.segment(segmentId).foreach { segment =>
          segment.chain.filterById(filterId) match {
            case top: NodeGraphFilter =>
              val updated = substituteGraph(top, path.tail, target)
              updateSegment(segmentId)(s => s.withFilterChain(s.chain.replaceFilterById(filterId, updated)))
            case _ =>
          }
        }
      case _: NodeGraphFilter.InGraph =>
    }
    if (project ne before) {
      undo.push(before)
    }
  }

  def graphFor(path: Vector[NodeGraphFilter.Owner]): NodeGraphFilter = {
    if (path.isEmpty) {
      null
    } else {
      val root: NodeGraphFilter = path.head match {
        case NodeGraphFilter.InChain(segmentId, filterId) =>
          timeline.segment(segmentId).map(_.chain.filterById(filterId)).collect { case filter: NodeGraphFilter => filter }.orNull
        case _ => null
      }
      if (root == null) {
        null
      } else {
        var current: NodeGraphFilter = root
        var valid: Boolean = true
        val steps = path.tail.iterator
        while (valid && steps.hasNext) {
          steps.next() match {
            case NodeGraphFilter.InGraph(nodeId) =>
              current.innerNodes.nodes.get(nodeId) match {
                case Some(filter: NodeGraphFilter) => current = filter
                case _ => valid = false
              }
            case _ => valid = false
          }
        }
        if (valid) current else null
      }
    }
  }

  private def substituteGraph(current: NodeGraphFilter,
                              steps: Vector[NodeGraphFilter.Owner],
                              target: NodeGraphFilter): NodeGraphFilter = {
    steps match {
      case Vector() => target
      case (step: NodeGraphFilter.InGraph) +: rest =>
        current.innerNodes.nodes.get(step.nodeId) match {
          case Some(child: NodeGraphFilter) =>
            val child2 = substituteGraph(child, rest, target)
            current.withInnerNodes(current.innerNodes.updateNode(step.nodeId)(_ => child2))
          case _ => current
        }
      case _ => current
    }
  }

  def applyProject(f: Project => Project): Unit = {
    val previousResources = project.resources
    project = f(project)
    for (i <- project.timeline.tracks.indices) {
      worker(i).onTrackChanged()
    }
    postResourcesChanged(previousResources, project.resources)
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

  def snapshot(): Unit = {
    undo.push(project)
    touch()
  }

  def worker(index: Int): TrackWorker = synchronized {
    workers.getOrElseUpdate(index, new TrackWorker(this, index))
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
