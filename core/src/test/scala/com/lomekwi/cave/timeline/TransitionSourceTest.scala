package com.lomekwi.cave.timeline

import com.lomekwi.cave.pipeline.{Clip, Content, Filter, Frame, Segment, Source, Transition, TransitionSource}
import com.lomekwi.cave.project.TestProject
import com.lomekwi.cave.ui.editpanel.tlarea.TlSegmentActor

import org.junit.jupiter.api.Assertions.{assertEquals, assertNull, assertSame, assertTrue, fail}
import org.junit.jupiter.api.{BeforeEach, Test}

/**
 * 转场源框架的模型级测试。框架在 [[TransitionSource.produce]] 里反查轨道布局，
 * 驱动两侧子源并把换算结果交给 [[TransitionSource.mix]]，覆盖如下。
 *  - 时间换算：两侧子源各自收到正确的片段内时间，progress 为转场内局部进度
 *  - 方向还原：后段兜底构造的转场源 mix 仍先起点侧后终点侧，progress 相应翻转
 *  - 无帧退化：一侧子源暂时无帧时退化为另一侧，两侧都无帧才交出无帧
 */
class TransitionSourceTest extends GdxTestBase {

  private var timeline: Timeline = null

  @BeforeEach
  def setUp(): Unit = {
    timeline = new TestProject().timeline
  }

  /** 帧上带产出时间，便于断言两侧子源各自收到的时间。 */
  class TimedFrame(trackIndex: Int, val producedAt: Long) extends Frame(trackIndex)

  private class ProbeSource(duration: Long, nullAt: Long => Boolean) extends Source[TimedFrame] {
    var lastSync: Long = -1L

    override def sync(time: Long, track: Track, segment: Segment): Unit = {
      lastSync = time
    }

    override protected def produce(time: Long, track: Track, segment: Segment): TimedFrame = {
      if (nullAt(time)) null else new TimedFrame(track.index, time)
    }

    override def getLengthPerExportFrame: Long = 1

    override def getDuration: Long = duration

    override def getDefaultDuration: Option[Long] = Some(duration)

    override def displayName: String = "probe"

    override def createTlSegmentActor(segment: Segment): TlSegmentActor = null
  }

  /**
   * canLead 为 false 时不能作为前段构造转场，放置成左侧内容即走后段兜底路径。
   */
  private class ProbeContent(duration: Long, canLead: Boolean, nullAt: Long => Boolean)
    extends Clip(new ProbeSource(duration, nullAt)) {

    def this(duration: Long, canLead: Boolean) = this(duration, canLead, _ => false)

    override def canCreateTransitionWith(other: Content): Boolean = canLead

    override def createTransition(other: Content): Transition = {
      // 测试布局中两侧都是探针内容，帧型必然相容
      new Transition(new ProbeTransitionSource(
        this.source.asInstanceOf[Source[TimedFrame]],
        other.source.asInstanceOf[Source[TimedFrame]])) {}
    }
  }

  private class ProbeTransitionSource(from: Source[? <: TimedFrame], to: Source[? <: TimedFrame])
    extends TransitionSource[TimedFrame, TimedFrame](from, to) {

    var lastFrom: TimedFrame = null
    var lastTo: TimedFrame = null
    var lastProgress: Float = -1f

    override def mix(fromFrame: TimedFrame, toFrame: TimedFrame, progress: Float): TimedFrame = {
      lastFrom = fromFrame
      lastTo = toFrame
      lastProgress = progress
      new TimedFrame((if (fromFrame != null) fromFrame else toFrame).trackIndex, -1L)
    }

    override def displayName: String = "probe-transition"
  }

  /** 透传滤镜，记录被拉取的次数与最近一帧，用于断言转场求值经过片段滤镜链。 */
  private class CountingFilter extends Filter[Frame] {
    var count: Int = 0
    var lastFrame: Frame = null

    addInPort(new FilterIn("输入"))
    addOutPort(new FilterOut("输出") {
      override def getData: Frame = {
        val frame = filterIn.getData
        count += 1
        lastFrame = frame
        frame
      }
    })

    override val name: String = "计数"
  }

  /** 左右两块内容重叠出转场；左块 [0,1000) origin 0，右块 [500,1500) origin 500。 */
  private def placedTrack(leftLead: Boolean, leftNullAt: Long => Boolean, rightNullAt: Long => Boolean)
  : (Track, Transition, ProbeTransitionSource) = {
    val track = timeline.getTrackOrCreate(0)
    val left = new ProbeContent(1000, leftLead, leftNullAt)
    val right = new ProbeContent(1000, canLead = true, rightNullAt)
    timeline.addOrThrow(track, left, 0L ~~ 1000L, 0L)
    timeline.addOrThrow(track, right, 500L ~~ 1500L, 500L)
    // 轨道不可变，编辑后取最新版本断言
    val current = timeline.getTrackOrCreate(0)
    val transition = current.get(600L) match {
      case t: Transition => t
      case other => fail("重叠处应是转场，实际: " + other + "；轨道: " + current)
    }
    (current, transition, transition.source.asInstanceOf[ProbeTransitionSource])
  }

  @Test
  def mixReceivesPerSideTimesAndProgress(): Unit = {
    val (track, transition, source) = placedTrack(leftLead = true, _ => false, _ => false)

    track.frameAt(transition, 600L)
    assertEquals(600L, source.lastFrom.producedAt)
    assertEquals(100L, source.lastTo.producedAt)
    assertEquals(0.2f, source.lastProgress, 1e-6f)

    track.frameAt(transition, 999L)
    assertEquals(999L, source.lastFrom.producedAt)
    assertEquals(499L, source.lastTo.producedAt)
    assertEquals(499f / 500f, source.lastProgress, 1e-6f)
  }

  @Test
  def produceConsumesSideFilterChains(): Unit = {
    val track = timeline.getTrackOrCreate(0)
    val left = new ProbeContent(1000, canLead = true)
    val right = new ProbeContent(1000, canLead = true)
    timeline.addOrThrow(track, left, 0L ~~ 1000L, 0L)
    timeline.addOrThrow(track, right, 500L ~~ 1500L, 500L)
    val current = timeline.getTrackOrCreate(0)
    val transition = current.get(600L) match {
      case t: Transition => t
      case other => fail("重叠处应是转场，实际: " + other + "；轨道: " + current)
    }
    val source = transition.source.asInstanceOf[ProbeTransitionSource]

    val filter = new CountingFilter
    left.attach(filter)
    current.frameAt(transition, 600L)

    assertEquals(1, filter.count)
    // mix 收到的是滤镜链输出的同一帧
    assertSame(filter.lastFrame, source.lastFrom)
  }

  @Test
  def flippedTransitionRestoresSidesAndReversesProgress(): Unit = {
    val (track, transition, source) = placedTrack(leftLead = false, _ => false, _ => false)
    // 兜底构造：转场源由右侧内容造出，[[TransitionSource.from]] 指向右内容
    val right = track.get(1200L) match {
      case c: Content => c
      case _ => fail("转场终点之外应是右内容")
    }
    assertTrue(source.from eq right.source)

    track.frameAt(transition, 600L)
    // [[mix]] 的视角仍是起点侧在前，`progress` 翻转
    assertEquals(600L, source.lastFrom.producedAt)
    assertEquals(100L, source.lastTo.producedAt)
    assertEquals(0.8f, source.lastProgress, 1e-6f)
  }

  @Test
  def passesNullSideToMixWhenOneSideHasNoFrame(): Unit = {
    val (track, transition, source) = placedTrack(leftLead = true, _ == 600L, _ => false)

    track.frameAt(transition, 600L)
    // 单侧无帧原样交给 mix，由效果决定表现
    assertNull(source.lastFrom)
    assertEquals(100L, source.lastTo.producedAt)
    assertEquals(0.2f, source.lastProgress, 1e-6f)
  }

  @Test
  def syncReachesBothSidesWithConvertedTimes(): Unit = {
    val (track, transition, _) = placedTrack(leftLead = true, _ => false, _ => false)
    val leftSource = track.get(200L).asInstanceOf[Content].source.asInstanceOf[ProbeSource]
    val rightSource = track.get(1200L).asInstanceOf[Content].source.asInstanceOf[ProbeSource]

    track.syncAt(transition, 600L)
    assertEquals(600L, leftSource.lastSync)
    assertEquals(100L, rightSource.lastSync)
  }

  @Test
  def syncForwardsCorrectlyWhenFlipped(): Unit = {
    val (track, transition, _) = placedTrack(leftLead = false, _ => false, _ => false)
    val leftSource = track.get(200L).asInstanceOf[Content].source.asInstanceOf[ProbeSource]
    val rightSource = track.get(1200L).asInstanceOf[Content].source.asInstanceOf[ProbeSource]

    track.syncAt(transition, 600L)
    // [[TransitionSource.from]] 指向右内容，转发仍按各自的原始配对换算
    assertEquals(600L, leftSource.lastSync)
    assertEquals(100L, rightSource.lastSync)
  }

  @Test
  def yieldsNoFrameWhenBothSidesHaveNoFrame(): Unit = {
    // 两侧子源收到的片段内时间不同：左 600、右 100
    val (track, transition, _) = placedTrack(leftLead = true, _ == 600L, _ == 100L)

    assertNull(track.frameAt(transition, 600L))
  }
}
