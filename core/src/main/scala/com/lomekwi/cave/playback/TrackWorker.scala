package com.lomekwi.cave.playback

import com.badlogic.gdx.Gdx
import com.google.common.eventbus.Subscribe
import com.lomekwi.cave.pipeline.GapFrame
import com.lomekwi.cave.project.ProjectSession
import com.lomekwi.cave.timeline.{Gap, Segment, Timeline}
import com.lomekwi.cave.util.i18n.*
import com.lomekwi.cave.util.i18n.BasicLang.*
import com.lomekwi.cave.util.i18n.TransTargetType.given

import java.util.concurrent.locks.LockSupport
import java.util.concurrent.{Future, Phaser}
import scala.compiletime.uninitialized

/**
 * 轨道线程，按时间独立推进播放头，把帧投到项目事件总线上，
 * 并用 Phaser 与消费方（预览、音频混音）做握手。
 */
class TrackWorker(private val context: ProjectSession, private val index: Int) extends Runnable {
  private final val gapFrame: GapFrame = new GapFrame(index)
  lazy val sinkPhaser: Phaser = new Phaser(1)
  var future: Future[?] = uninitialized
  @volatile private var workerThread: Thread = uninitialized
  @volatile private var updateNeeded: Boolean = false

  private def timeline: Timeline = context.timeline

  context.bus.register(this)
//TODO：重构。这玩意太混乱了。搞个状态机也许不错。
  override def run(): Unit = {
    workerThread = Thread.currentThread()
    Gdx.app.log("Track" + index, ("轨道线程启动: ".in(Zh) | "Track thread started: ".in(En)).str() + timeline.getTrackOrCreate(index))
    try {
      val p = context.playhead
      while (!Thread.currentThread().isInterrupted) {
        val track = timeline.getTrackOrCreate(index)
        var t: Long = p.getTime
        // 当前时刻实际生效的条目。内容的区间覆盖转场区，不能用区间包含关系判断是否还在原片段上
        val current: Segment = track.get(t) match {
          case s: Segment => s
          case _: Gap => null
        }
        if (p.state != PlayState.Playing) {
          Gdx.app.debug("Track" + index, ("因为播放头而尝试park...".in(Zh) | "Trying to park due to the playhead...".in(En)).str())

          var f: com.lomekwi.cave.pipeline.Frame = null
          if (current != null) {
            track.syncAt(current, t)
            f = track.frameAt(current, t)
          }
          context.bus.post(java.util.Objects.requireNonNullElse(f, gapFrame))
          if (p.state == PlayState.Seeking) {
            p.reportSeekDone(p.getSeekVersion, index, timeline.getTrackCount)
          }

          LockSupport.park()
        } else {
          updateNeeded = false
          if (current != null) {
            val s = current
            Gdx.app.debug("Track" + index, ("找到源: ".in(Zh) | "Found source: ".in(En)).str() + s)
            track.syncAt(s, t)
            // 独占播放的终点：内容被右侧转场遮盖时只播到转场起点，不能一路播过转场
            val end: Long = track.soloEndOf(s)
            var owns: Boolean = true
            while (t < end && owns && !updateNeeded && !Thread.currentThread().isInterrupted) {
              t = context.playhead.getTime
              // 片段可能已被搬到别的轨道，本线程不再驱动它，交由外层重新解析
              val cur = timeline.getTrackOrCreate(index)
              if (!cur.contains(s)) {
                owns = false
              } else {
                val frame = cur.frameAt(s, t)
                if (!updateNeeded && frame != null) {
                  context.bus.post(frame)
                  val phase = sinkPhaser.arrive()
                  try {
                    sinkPhaser.awaitAdvanceInterruptibly(phase)
                  } catch {
                    case _: InterruptedException =>
                      Thread.currentThread().interrupt()
                  }
                }
              }
            }
          } else {
            context.bus.post(gapFrame)
            val gapEnd: Long = track.intervalAt(t).hi
            val parkTime: Long = if (gapEnd == Long.MaxValue) Long.MaxValue else Math.max((gapEnd - t) * 1000, 1)
            Gdx.app.debug("Track" + index, ("轨道线程等待: ".in(Zh) | "Track thread waiting: ".in(En)).str() + parkTime / 1e9 + ("秒".in(Zh) | "s".in(En)).str())
            LockSupport.parkNanos(parkTime)
          }
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
