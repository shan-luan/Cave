package com.lomekwi.cave.timeline.playback

import com.google.common.eventbus.EventBus

class Playhead(@transient private val projEventBus: EventBus) {

  @volatile private var anchor: Long = 0
  @volatile private var frozenTime: Long = 0L
  @volatile private var playState: PlayState = PlayState.Paused
  // SEEKING 会话的目标状态与版本号
  @volatile private var seekTargetState: PlayState = PlayState.Paused
  @volatile private var seekVersion: Long = 0L

  def state: PlayState = playState

  def state_=(state: PlayState): Unit = {
    if (state != playState) {
      if (state == PlayState.Playing) {
        anchor = System.nanoTime() - frozenTime
      } else if (playState == PlayState.Playing) {
        frozenTime = System.nanoTime() - anchor
      }
      // SEEKING 期间 anchor 失效，从 SEEKING 进入 Paused 保留 frozenTime 的 seek 目标

      playState = state
      projEventBus.post(PlayStateChangedEvent)
    }
  }

  def getSeekVersion: Long = seekVersion

  def seek(time: Long): Unit = {
    var t = time
    t *= 1000

    frozenTime = t
    seekVersion += 1
    if (playState != PlayState.Seeking) {
      seekTargetState = playState
      playState = PlayState.Seeking
      projEventBus.post(PlayStateChangedEvent)
    }

    projEventBus.post(SeekEvent)
  }

  /** 所有轨道 sync 到 seek 目标后由 [[Timeline]] 聚合调用，恢复 seek 前的状态 */
  def finishSeek(): Unit = {
    state = seekTargetState
  }

  def getTime: Long = getNanoTime / 1000

  private def getNanoTime: Long = {
    if (playState == PlayState.Playing) {
      System.nanoTime() - anchor
    } else {
      frozenTime
    }
  }
}
