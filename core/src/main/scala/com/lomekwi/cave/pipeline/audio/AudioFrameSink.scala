package com.lomekwi.cave.pipeline.audio

import com.google.common.eventbus.Subscribe
import com.lomekwi.cave.app.App
import com.lomekwi.cave.project.ProjectBackgroundedEvent
import com.lomekwi.cave.project.ProjectFrontedEvent
import com.lomekwi.cave.resource.decoder.AudDecRes

import java.util
import java.util.concurrent.{Future, LinkedBlockingQueue, TimeUnit}
import scala.compiletime.uninitialized

class AudioFrameSink {
  @volatile private var afm: AudioFrameSink.AudioFrameMixer = new AudioFrameSink.AudioFrameMixer()
  private var currentFuture: Future[?] = uninitialized

  @Subscribe
  def sink(frame: AudFrame): Unit = {
    frame.track.getWorker.getSinkPhaser.register()
    afm.submit(frame)
  }

  @Subscribe
  def onProjectFronted(event: ProjectFrontedEvent): Unit = {
    stopMixer()
    afm = new AudioFrameSink.AudioFrameMixer()
    currentFuture = App.workerExecutor.submit(afm)
  }

  @Subscribe
  def onProjectBackgrounded(event: ProjectBackgroundedEvent): Unit = {
    stopMixer()
  }

  private def stopMixer(): Unit = {
    val f = currentFuture
    currentFuture = null
    if (f != null) {
      afm.stop()
      try {
        f.get(200, TimeUnit.MILLISECONDS)
      } catch {
        case _: Exception =>
          f.cancel(true)
      }
    }
  }
}

object AudioFrameSink {
  private[audio] class AudioFrameMixer extends Runnable {
    private final val frames: LinkedBlockingQueue[AudFrame] = new LinkedBlockingQueue[AudFrame]()
    private final val output: Array[Float] = new Array[Float](AudDecRes.FRAME_SIZE)
    @volatile private var stopped: Boolean = false

    def stop(): Unit = {
      stopped = true
    }

    override def run(): Unit = {
      while (!stopped) {
        util.Arrays.fill(output, 0f)
        var f: AudFrame = null
        try {
          f = frames.poll(AudioFrameMixer.POLL_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
        } catch {
          case _: InterruptedException =>
            return
        }
        if (f != null) {
          var continueLoop = true
          while (continueLoop) {
            var j = 0
            for (sample <- f.getSamples) {
              output(j) += sample
              j += 1
            }
            f.track.getWorker.getSinkPhaser.arriveAndDeregister()
            if (stopped || Thread.currentThread().isInterrupted) {
              continueLoop = false
            } else {
              f = frames.poll()
              continueLoop = f != null
            }
          }
          if (stopped || Thread.currentThread().isInterrupted) {
            return
          }
          clamp(output)
          App.audioOut.writeSamples(output)
        }
      }
    }

    private def clamp(samples: Array[Float]): Unit = {
      var i = 0
      while (i < samples.length) {
        samples(i) = Math.max(-1.0f, Math.min(1.0f, samples(i)))
        i += 1
      }
    }

    def submit(f: AudFrame): Unit = {
      frames.add(f)
    }
  }

  private object AudioFrameMixer {
    private final val POLL_TIMEOUT_MILLIS = 20L
  }
}
