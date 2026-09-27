package com.lomekwi.cave.app

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.audio.AudioDevice

class AppAudioOut {
  final val audioDevice: AudioDevice = Gdx.audio.newAudioDevice(AppAudioOut.SAMPLE_RATE, false)

  def writeSamples(samples: Array[Float]): Unit = this.synchronized {
    audioDevice.writeSamples(samples, 0, samples.length)
  }
}

object AppAudioOut {
  final val SAMPLE_RATE = 44100
}
