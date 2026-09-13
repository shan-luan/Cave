package com.lomekwi.cave.app

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.audio.AudioDevice

class AppAudioOut {
  private final val audioDevice: AudioDevice = Gdx.audio.newAudioDevice(AppAudioOut.SAMPLE_RATE, false)

  def getAudioDevice(): AudioDevice = {
    audioDevice
  }

  def writeSamples(samples: Array[Float]): Unit = this.synchronized {
    audioDevice.writeSamples(samples, 0, samples.length)
  }
}

object AppAudioOut {
  final val SAMPLE_RATE = 44100
}
