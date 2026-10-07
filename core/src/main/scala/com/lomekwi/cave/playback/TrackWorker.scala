package com.lomekwi.cave.playback

import com.badlogic.gdx.Gdx
import com.lomekwi.cave.pipeline.{Gap, GapFrame, Segment}
import com.lomekwi.cave.timeline.Timeline
import com.google.common.eventbus.Subscribe

import java.util.concurrent.{Future, Phaser}
import java.util.concurrent.locks.LockSupport

import scala.compiletime.uninitialized

/**
 * 轨道线程，按时间独立推进播放头，把帧投到项目事件总线上，
 * 并用 Phaser 与消费方（预览、音频混音）做握手。
 */
class TrackWorker(private val timeline: Timeline, private val index: Int) extends Runnable {
  private final val gapFrame: GapFrame = new GapFrame(index)
  var sinkPhaser: Phaser = uninitialized
  var future: Future[?] = uninitialized
  @volatile private var workerThread: Thread = uninitialized
  @volatile private var updateNeeded: Boolean = false

  timeline.project.projEventBus.register(this)

  override def run(): Unit = {
    workerThread = Thread.currentThread()
    sinkPhaser = new Phaser(1)
    Gdx.app.log("Track" + index, "轨道线程启动: " + timeline.getTrackOrCreate(index))
    try {
      val p = timeline.project.playhead
      while (!Thread.currentThread().isInterrupted) {
        val track = timeline.getTrackOrCreate(index)
        var t: Long = p.getTime
        // 当前时刻实际生效的条目。内容的区间覆盖转场区，不能用区间包含关系判断是否还在原片段上
        val current: Segment = track.get(t) match {
          case s: Segment => s
          case _: Gap => null
        }
        if (p.state != PlayState.Playing) {
          Gdx.app.debug("Track" + index, "因为播放头而尝试park...")

          var f: com.lomekwi.cave.pipeline.Frame = null
          if (current != null) {
            track.syncAt(current, t)
            f = track.frameAt(current, t)
          }
          timeline.project.projEventBus.post(java.util.Objects.requireNonNullElse(f, gapFrame))
          if (p.state == PlayState.Seeking) {
            p.reportSeekDone(p.getSeekVersion, index, timeline.getTrackCount)
          }

          LockSupport.park()
        } else {
          updateNeeded = false
          if (current != null) {
            val s = current
            Gdx.app.debug("Track" + index, "找到源: " + s)
            track.syncAt(s, t)
            // 独占播放的终点：内容被右侧转场遮盖时只播到转场起点，不能一路播过转场
            val end: Long = track.soloEndOf(s)
            while (t < end && !updateNeeded && !Thread.currentThread().isInterrupted) {
              t = timeline.project.playhead.getTime
              val frame = track.frameAt(s, t)
              if (!updateNeeded && frame != null) {
                timeline.project.projEventBus.post(frame)
                val phase = sinkPhaser.arrive()
                try {
                  sinkPhaser.awaitAdvanceInterruptibly(phase)
                } catch {
                  case _: InterruptedException =>
                    Thread.currentThread().interrupt()
                }
              }
            }
          } else {
            timeline.project.projEventBus.post(gapFrame)
            val gapEnd: Long = track.rangeAt(t).hi
            val parkTime: Long = if (gapEnd == Long.MaxValue) Long.MaxValue else Math.max((gapEnd - t) * 1000, 1)
            Gdx.app.debug("Track" + index, "轨道线程等待: " + parkTime / 1e9 + "秒")
            LockSupport.parkNanos(parkTime)
          }
        }
      }
    } catch {
      case e: Exception =>
        if (!e.isInstanceOf[InterruptedException]) {
          Gdx.app.error("Track" + index, "在更新轨道时发生错误", e)
          Gdx.app.postRunnable(() => {
            throw new RuntimeException(e)
          })
        }
    } finally {
      workerThread = null
      Gdx.app.log("Track" + index, "轨道线程结束: " + timeline.getTrackOrCreate(index))
    }
  }
  @Subscribe
  def onPlayStateChanged(event: PlayStateChangedEvent): Unit = {
    update()
  }
  @Subscribe
  def onRefreshRequested(event: RefreshRequestEvent): Unit = {
    update()
  }
  @Subscribe
  def onSeek(event: SeekEvent): Unit = {
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
