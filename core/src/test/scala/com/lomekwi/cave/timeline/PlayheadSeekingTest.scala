package com.lomekwi.cave.timeline

import com.lomekwi.cave.project.TestProject
import com.lomekwi.cave.timeline.playback.PlayState
import com.lomekwi.cave.util.Units.SECOND

import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}
import org.junit.jupiter.api.Test

/**
 * seek 的 SEEKING 过渡语义：seek 后进入 Seeking，所有轨道 sync 到目标后恢复 seek 前的状态。
 */
class PlayheadSeekingTest extends GdxTestBase {

  private final val SETTLE_MILLIS = 500L

  @Test
  def seekFromPausedRestoresPausedAtTarget(): Unit = {
    val project = new TestProject()
    val duration = SECOND
    val cont = new PlayheadSeekingTest.SyncTimeCont(duration)
    val track = project.timeline.getTrackOrCreate(0)
    assertEquals(0L, project.timeline.tryAdd(track, cont, 0 ~~ duration, 0), "源应被加入轨道")

    val thread = new Thread(track.getWorker, "seek-paused-test")
    thread.setDaemon(true)
    thread.start()
    try {
      val target = duration / 2
      project.playhead.seek(target)
      assertEquals(PlayState.Seeking, project.playhead.state, "seek 后应进入 SEEKING")
      assertTrue(awaitState(project, PlayState.Paused), "轨道 sync 到目标后应恢复 Paused")
      assertEquals(target, cont.getLastSyncTime, "轨道应 sync 到 seek 目标")
    } finally {
      thread.interrupt()
      thread.join(2000)
      project.close()
    }
  }

  @Test
  def seekDuringPlayingResumesPlayingAtTarget(): Unit = {
    val project = new TestProject()
    val duration = SECOND
    val cont = new PlayheadSeekingTest.SyncTimeCont(duration)
    val track = project.timeline.getTrackOrCreate(0)
    assertEquals(0L, project.timeline.tryAdd(track, cont, 0 ~~ duration, 0), "源应被加入轨道")

    val thread = new Thread(track.getWorker, "seek-playing-test")
    thread.setDaemon(true)
    thread.start()
    try {
      project.playhead.state = PlayState.Playing
      val target = duration / 2
      project.playhead.seek(target)
      assertEquals(PlayState.Seeking, project.playhead.state, "播放中 seek 也应进入 SEEKING")
      assertTrue(awaitState(project, PlayState.Playing), "恢复后应为 Playing")
      assertTrue(project.playhead.getTime >= target, "恢复后应从 seek 目标处继续")
    } finally {
      thread.interrupt()
      thread.join(2000)
      project.close()
    }
  }

  @Test
  def seekWaitsForAllTracksIncludingGaps(): Unit = {
    val project = new TestProject()
    val duration = SECOND
    val cont = new PlayheadSeekingTest.SyncTimeCont(duration)
    val track0 = project.timeline.getTrackOrCreate(0)
    assertEquals(0L, project.timeline.tryAdd(track0, cont, 0 ~~ duration, 0), "源应被加入轨道")
    // 只产生 gap 的空轨道同样要上报完成
    val track1 = project.timeline.getTrackOrCreate(1)

    val thread0 = new Thread(track0.getWorker, "seek-multi-0")
    thread0.setDaemon(true)
    val thread1 = new Thread(track1.getWorker, "seek-multi-1")
    thread1.setDaemon(true)
    thread0.start()
    thread1.start()
    try {
      val target = duration / 2
      project.playhead.seek(target)
      assertEquals(PlayState.Seeking, project.playhead.state)
      assertTrue(awaitState(project, PlayState.Paused), "所有轨道（含 gap 轨道）sync 完才应恢复")
      assertEquals(target, cont.getLastSyncTime, "有内容的轨道应 sync 到目标")
    } finally {
      thread0.interrupt()
      thread1.interrupt()
      thread0.join(2000)
      thread1.join(2000)
      project.close()
    }
  }

  private def awaitState(project: TestProject, expected: PlayState): Boolean = {
    var remain = SETTLE_MILLIS * 4
    while (project.playhead.state != expected && remain > 0) {
      Thread.sleep(10)
      remain -= 10
    }
    project.playhead.state == expected
  }
}

object PlayheadSeekingTest {

  /** 记录最后一次 sync 的源内时间。 */
  class SyncTimeCont(duration: Long) extends TestCont(duration) {
    private var lastSyncTime: Long = -1L

    override def sync(time: Long, track: Track): Unit = this.synchronized {
      lastSyncTime = time
    }

    def getLastSyncTime: Long = this.synchronized(lastSyncTime)
  }
}
