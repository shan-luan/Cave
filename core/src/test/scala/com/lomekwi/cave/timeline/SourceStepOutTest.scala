package com.lomekwi.cave.timeline

import com.lomekwi.cave.project.TestProject
import com.lomekwi.cave.util.Units.SECOND

import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}
import org.junit.jupiter.api.Test

/**
 * 播放头离开片段时 `Source.onStepOut` 的触发时机，自然播放越过片段终点，
 * 以及 seek 把播放头挪到片段区间之外（暂停状态下同样如此）。
 */
class SourceStepOutTest extends GdxTestBase {

  private final val SETTLE_MILLIS = 300L

  @Test
  def stepOutFiresOnlyWhenPlayheadLeavesSegment(): Unit = {
    val project = new TestProject()
    val timeline = project.timeline
    val duration = SECOND / 100
    val source = new SourceStepOutTest.RecordingSource(duration)
    val track = timeline.getTrackOrCreate(0)
    assertEquals(0L, timeline.tryAdd(track, source, Interval(0, duration), 0), "片段应被加入轨道")

    val thread = new Thread(track.getWorker, "step-out-test")
    thread.setDaemon(true)
    thread.start()
    try {
      project.playhead.seek(duration / 2)
      Thread.sleep(SETTLE_MILLIS)
      assertTrue(source.getSyncCount > 0, "轨迹线程应已处理暂停后的位置")
      assertEquals(0, source.getStepOutCount, "片段内的 seek 不应触发 onStepOut")

      project.playhead.seek(duration + SECOND / 10)
      assertTrue(source.awaitStepOut(SETTLE_MILLIS * 4), "暂停时 seek 离开片段应触发 onStepOut")
      assertEquals(1, source.getStepOutCount)
      assertEquals(duration + SECOND / 10, source.getLastStepOutTime, "应报告离开时的源内时间")

      project.playhead.seek(0)
      project.playhead.setPlaying(true)
      assertTrue(source.awaitStepOut(SECOND), "自然播放越过片段终点应触发 onStepOut")
      assertEquals(2, source.getStepOutCount)
      assertTrue(source.getLastStepOutTime >= duration, "越界时间不应早于片段终点")
      project.playhead.setPlaying(false)
    } finally {
      thread.interrupt()
      thread.join(2000)
      project.close()
    }
  }
}

object SourceStepOutTest {

  /** 记录 sync / onStepOut 的调用，供断言检查。 */
  class RecordingSource(duration: Long) extends TestSource(duration) {
    private var syncCount = 0
    private var stepOutCount = 0
    private var lastStepOutTime = 0L

    override def sync(time: Long, track: Track): Unit = this.synchronized {
      syncCount += 1
    }

    override def onStepOut(time: Long, track: Track): Unit = this.synchronized {
      stepOutCount += 1
      lastStepOutTime = time
      notifyAll()
    }

    def getSyncCount: Int = this.synchronized(syncCount)

    def getStepOutCount: Int = this.synchronized(stepOutCount)

    def getLastStepOutTime: Long = this.synchronized(lastStepOutTime)

    /** 等到下一次 onStepOut；超时返回 false。 */
    def awaitStepOut(timeoutMillis: Long): Boolean = this.synchronized {
      val target = stepOutCount + 1
      var remain = timeoutMillis
      while (stepOutCount < target && remain > 0) {
        val before = System.nanoTime()
        this.wait(remain)
        remain -= (System.nanoTime() - before) / 1000000L
      }
      stepOutCount >= target
    }
  }
}
