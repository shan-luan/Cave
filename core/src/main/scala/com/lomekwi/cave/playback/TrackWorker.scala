package com.lomekwi.cave.playback

import com.badlogic.gdx.Gdx
import com.google.common.eventbus.Subscribe
import com.lomekwi.cave.pipeline.{Frame, GapFrame}
import com.lomekwi.cave.project.ProjectSession
import com.lomekwi.cave.timeline.{Gap, Instant, Segment, Timeline, Track}
import com.lomekwi.cave.util.i18n.*
import com.lomekwi.cave.util.i18n.BasicLang.*

import java.util.concurrent.locks.LockSupport
import java.util.concurrent.{Future, Phaser}
import scala.compiletime.uninitialized

/**
 * 轨道线程，按时间独立推进播放头。
 */
class TrackWorker(private val session: ProjectSession, private val index: Int) extends Runnable {
  private final val gapFrame: GapFrame = new GapFrame(index)
  lazy val sinkPhaser: Phaser = new Phaser(1)
  var future: Future[?] = uninitialized
  @volatile private var workerThread: Thread = uninitialized
  @volatile private var updateNeeded: Boolean = false

  private def timeline: Timeline = session.timeline

  session.bus.register(this)

  private enum State {
    case Evaluate
    case Preview(track: Track, segment: Option[Segment], time: Instant)
    case PlayingSegment(segment: Segment, end: Instant)
    case PlayingGap(end: Instant)
    case Stopped
  }

  override def run(): Unit = {
    workerThread = Thread.currentThread()
    Gdx.app.log("Track" + index, ("轨道线程启动: ".in(Zh) | "Track thread started: ".in(En)).str() + timeline.getTrackOrCreate(index))
    try {
      var state: State = State.Evaluate
      while (state != State.Stopped && !Thread.currentThread().isInterrupted) {
        state = state match {
          case State.Evaluate => evaluate()
          case State.Preview(track, segment, time) => preview(track, segment, time)
          case State.PlayingSegment(segment, end) => playSegment(segment, end)
          case State.PlayingGap(end) => playGap(end)
          case State.Stopped => State.Stopped
        }
      }
    } catch {
      case e: Exception =>
        if (!e.isInstanceOf[InterruptedException]) {
          Gdx.app.error("Track" + index, ("在更新轨道时发生错误".in(Zh) | "An error occurred while updating the track".in(En)).str(), e)
          Gdx.app.postRunnable(() => {
            throw new RuntimeException(e)
          })
        }
    } finally {
      workerThread = null
      Gdx.app.log("Track" + index, ("轨道线程结束: ".in(Zh) | "Track thread finished: ".in(En)).str() + timeline.getTrackOrCreate(index))
    }
  }

  private def evaluate(): State = {
    updateNeeded = false
    val p = session.playhead
    val t = p.getTime
    val track = timeline.getTrackOrCreate(index)
    track.get(t) match {
      case s: Segment =>
        if (p.state == PlayState.Playing) {
          Gdx.app.debug("Track" + index, ("找到源: ".in(Zh) | "Found source: ".in(En)).str() + s)
          track.syncAt(s, t)
          State.PlayingSegment(s, track.soloEndOf(s))
        } else {
          State.Preview(track, Some(s), t)
        }
      case _: Gap =>
        if (p.state == PlayState.Playing) {
          State.PlayingGap(track.intervalAt(t).hi)
        } else {
          State.Preview(track, None, t)
        }
    }
  }

  private def preview(track: Track, segment: Option[Segment], t: Instant): State = {
    Gdx.app.debug("Track" + index, ("因为播放头而尝试park...".in(Zh) | "Trying to park due to the playhead...".in(En)).str())
    val f: Frame = segment match {
      case Some(s) =>
        track.syncAt(s, t)
        track.frameAt(s, t)
      case None => null
    }
    session.bus.post(java.util.Objects.requireNonNullElse(f, gapFrame))
    val p = session.playhead
    if (p.state == PlayState.Seeking) {
      p.reportSeekDone(p.getSeekVersion, index, timeline.getTrackCount)
    }
    LockSupport.park()
    State.Evaluate
  }

  private def playSegment(segment: Segment, end: Instant): State = {
    val t = session.playhead.getTime
    val cur = timeline.getTrackOrCreate(index)
    if (t >= end || updateNeeded || !cur.contains(segment)) {
      State.Evaluate
    } else {
      val frame = cur.frameAt(segment, t)
      if (updateNeeded) {
        State.Evaluate
      } else if (frame == null) {
        State.PlayingSegment(segment, end)
      } else {
        session.bus.post(frame)
        val phase = sinkPhaser.arrive()
        try {
          sinkPhaser.awaitAdvanceInterruptibly(phase)
        } catch {
          case _: InterruptedException =>
            Thread.currentThread().interrupt()
        }
        if (Thread.currentThread().isInterrupted) {
          State.Stopped
        } else if (updateNeeded) {
          State.Evaluate
        } else {
          State.PlayingSegment(segment, end)
        }
      }
    }
  }

  private def playGap(end: Instant): State = {
    session.bus.post(gapFrame)
    val t = session.playhead.getTime
    val parkTime: Long = if (end == Instant.MaxValue) Long.MaxValue else Math.max(((end - t) * 1000).value, 1)
    Gdx.app.debug("Track" + index, ("轨道线程等待: ".in(Zh) | "Track thread waiting: ".in(En)).str() + parkTime / 1e9 + ("秒".in(Zh) | "s".in(En)).str())
    LockSupport.parkNanos(parkTime)
    State.Evaluate
  }
  @Subscribe
  def onRefreshRequested(event: RefreshRequestEvent): Unit = {
    update()
  }
  private[cave] def onTrackChanged(): Unit = {
    update()
  }
  private def update(): Unit = {
    val t = workerThread
    if (t != null) {
      LockSupport.unpark(t)
    }
    updateNeeded = true
  }
}
