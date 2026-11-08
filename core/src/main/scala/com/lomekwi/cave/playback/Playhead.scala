package com.lomekwi.cave.playback

import com.badlogic.gdx.Gdx
import com.google.common.eventbus.EventBus
import com.lomekwi.cave.timeline.{Instant, Timespan}

import scala.collection.mutable

class Playhead(@transient private val projEventBus: EventBus) {

  @volatile private var anchor: Long = 0
  @volatile private var frozenTime: Long = 0L
  @volatile private var playState: PlayState = PlayState.Paused
  @volatile private var seekTargetState: PlayState = PlayState.Paused
  @volatile private var seekVersion: Long = 0L
  @volatile private var scrubSessions: Int = 0
  private var seekSessionVersion: Long = -1L
  private val seekDoneIndexes: mutable.HashSet[Int] = mutable.HashSet.empty[Int]

  def state: PlayState = playState

  def state_=(state: PlayState): Unit = {
    if (state != playState) {
      if (state == PlayState.Playing) {
        anchor = System.nanoTime() - frozenTime
      } else if (playState == PlayState.Playing) {
        frozenTime = System.nanoTime() - anchor
      }

      playState = state
      projEventBus.post(RefreshRequestEvent)
    }
  }

  def getSeekVersion: Long = seekVersion

  def seek(time: Instant): Unit = {
    var t = time.value
    t *= 1000

    frozenTime = t
    seekVersion += 1
    if (playState != PlayState.Seeking) {
      seekTargetState = playState
      playState = PlayState.Seeking
    }

    projEventBus.post(RefreshRequestEvent)
  }

  /**
   * 轨道线程在 SEEKING 下完成一轮 sync 后调用。
   */
  def reportSeekDone(version: Long, index: Int, trackCount: Int): Unit = synchronized {
    if (version >= seekSessionVersion) {
      if (version > seekSessionVersion) {
        seekSessionVersion = version
        seekDoneIndexes.clear()
      }
      if (seekDoneIndexes.add(index) && seekDoneIndexes.size >= trackCount) {
        seekDoneIndexes.clear()
        Gdx.app.postRunnable { () =>
          if (state == PlayState.Seeking && getSeekVersion == version) {
            finishSeek()
          }
        }
      }
    }
  }

  /** 聚齐后恢复 seek 前的状态。 */
  private def finishSeek(): Unit = {
    if (scrubSessions > 0) return
    state = seekTargetState
  }

  /** 拖拽播放头会话开始。 */
  def beginScrub(): Unit = {
    scrubSessions += 1
  }

  /** 拖拽播放头会话结束。 */
  def endScrub(): Unit = {
    scrubSessions -= 1
    if (scrubSessions == 0 && playState == PlayState.Seeking) {
      seek(getTime)
    }
  }

  def getTime: Instant = Instant.At(Timespan(getNanoTime / 1000))

  private def getNanoTime: Long = {
    if (playState == PlayState.Playing) {
      System.nanoTime() - anchor
    } else {
      frozenTime
    }
  }
}
