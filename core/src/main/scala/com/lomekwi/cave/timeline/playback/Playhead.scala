package com.lomekwi.cave.timeline.playback

import com.google.common.eventbus.EventBus

class Playhead(@transient private val projEventBus: EventBus) {

  @volatile private var anchor: Long = 0
  @volatile private var frozenTime: Long = 0L
  @volatile private var playing: Boolean = false

  def setPlaying(playing: Boolean): Unit = {
    if (playing == isPlaying()) return

    if (playing) {
      anchor = System.nanoTime() - frozenTime
    } else {
      frozenTime = System.nanoTime() - anchor
    }

    this.playing = playing
    projEventBus.post(PlayStateChangedEvent.INSTANCE)
  }

  def isPlaying(): Boolean = playing

  def seek(time: Long): Unit = {
    var t = time
    t *= 1000

    if (isPlaying()) {
      anchor = System.nanoTime() - t
    } else {
      frozenTime = t
    }

    projEventBus.post(SeekEvent.INSTANCE)
  }

  def getTime(): Long = getNanoTime() / 1000

  private def getNanoTime(): Long = {
    if (isPlaying()) {
      System.nanoTime() - anchor
    } else {
      frozenTime
    }
  }
}
