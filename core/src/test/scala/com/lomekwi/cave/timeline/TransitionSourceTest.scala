package com.lomekwi.cave.timeline

import com.lomekwi.cave.pipeline.{Filter, Frame, Source, TransitionSource}
import com.lomekwi.cave.project.{IdSource, TestIds, TestProject}
import com.lomekwi.cave.ui.editpanel.tlarea.TlSegmentActor

/**
 * 转场源框架的模型级测试。框架在 [[TransitionSource.produce]] 里反查轨道布局，
 * 驱动两侧子源并把换算结果交给 [[TransitionSource.mix]]。
 */
class TransitionSourceTest extends GdxTestBase {

  private var timeline: TestTimeline = null

  override def beforeEach(context: BeforeEach): Unit = {
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
    extends Clip(TestIds.nextId(), new ProbeSource(duration, nullAt)) {

    def this(duration: Long, canLead: Boolean) = this(duration, canLead, _ => false)

    override def canCreateTransitionWith(other: Content): Boolean = canLead

    override def createTransition(other: Content)(using ids: IdSource): Transition = {
      // 测试布局中两侧都是探针内容，帧型必然相容
      new Transition(ids.nextId(), new ProbeTransitionSource(
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

  test("mix 收到两侧各自的时间与进度") {
    val (track, transition, source) = placedTrack(leftLead = true, _ => false, _ => false)

    track.frameAt(transition, 600L)
    assertEquals(source.lastFrom.producedAt, 600L)
    assertEquals(source.lastTo.producedAt, 100L)
    assertEqualsFloat(source.lastProgress, 0.2f, 1e-6f)

    track.frameAt(transition, 999L)
    assertEquals(source.lastFrom.producedAt, 999L)
    assertEquals(source.lastTo.producedAt, 499L)
    assertEqualsFloat(source.lastProgress, 499f / 500f, 1e-6f)
  }

  test("产出时使用两侧的滤镜链") {
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

    assertEquals(filter.count, 1)
    // mix 收到的是滤镜链输出的同一帧
    assert((filter.lastFrame) eq (source.lastFrom))
  }

  test("翻转转场恢复两侧并反转进度") {
    val (track, transition, source) = placedTrack(leftLead = false, _ => false, _ => false)
    // 兜底构造：转场源由右侧内容造出，[[TransitionSource.from]] 指向右内容
    val right = track.get(1200L) match {
      case c: Content => c
      case _ => fail("转场终点之外应是右内容")
    }
    assert(source.from eq right.source)

    track.frameAt(transition, 600L)
    // [[mix]] 的视角仍是起点侧在前，`progress` 翻转
    assertEquals(source.lastFrom.producedAt, 600L)
    assertEquals(source.lastTo.producedAt, 100L)
    assertEqualsFloat(source.lastProgress, 0.8f, 1e-6f)
  }

  test("一侧无帧时向 mix 传入空侧") {
    val (track, transition, source) = placedTrack(leftLead = true, _ == 600L, _ => false)

    track.frameAt(transition, 600L)
    // 单侧无帧原样交给 mix，由效果决定表现
    assert((source.lastFrom) == null)
    assertEquals(source.lastTo.producedAt, 100L)
    assertEqualsFloat(source.lastProgress, 0.2f, 1e-6f)
  }

  test("sync 用换算后的时间到达两侧") {
    val (track, transition, _) = placedTrack(leftLead = true, _ => false, _ => false)
    val leftSource = track.get(200L).asInstanceOf[Content].source.asInstanceOf[ProbeSource]
    val rightSource = track.get(1200L).asInstanceOf[Content].source.asInstanceOf[ProbeSource]

    track.syncAt(transition, 600L)
    assertEquals(leftSource.lastSync, 600L)
    assertEquals(rightSource.lastSync, 100L)
  }

  test("翻转后 sync 正确转发") {
    val (track, transition, _) = placedTrack(leftLead = false, _ => false, _ => false)
    val leftSource = track.get(200L).asInstanceOf[Content].source.asInstanceOf[ProbeSource]
    val rightSource = track.get(1200L).asInstanceOf[Content].source.asInstanceOf[ProbeSource]

    track.syncAt(transition, 600L)
    // [[TransitionSource.from]] 指向右内容，转发仍按各自的原始配对换算
    assertEquals(leftSource.lastSync, 600L)
    assertEquals(rightSource.lastSync, 100L)
  }

  test("两侧都无帧时不产出帧") {
    // 两侧子源收到的片段内时间不同：左 600、右 100
    val (track, transition, _) = placedTrack(leftLead = true, _ == 600L, _ == 100L)

    assert((track.frameAt(transition, 600L)) == null)
  }
}
