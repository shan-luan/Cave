package com.lomekwi.cave.timeline

import com.lomekwi.cave.pipeline.{Filter, Frame, Source, TransitionSource}
import com.lomekwi.cave.project.{IdSource, TestIds, TestProject}
import com.lomekwi.cave.ui.editpanel.tlarea.TlSegmentActor

/**
 * 转场源框架的模型级测试。
 */
class TransitionSourceTest extends GdxTestBase {

  private var timeline: TestTimeline = null

  private def at(value: Long): Instant = Instant.At(Timespan(value))

  private def span(value: Long): Timespan = Timespan(value)

  override def beforeEach(context: BeforeEach): Unit = {
    timeline = new TestProject().timeline
  }

  /** 帧上带产出时间。 */
  class TimedFrame(trackIndex: Int, val producedAt: Timespan) extends Frame(trackIndex)

  private class ProbeSource(duration: Long, nullAt: Timespan => Boolean) extends Source[TimedFrame] {
    var lastSync: Timespan = Timespan.MinValue

    override def sync(time: Timespan, track: Track, segment: Segment): Unit = {
      lastSync = time
    }

    override protected def produce(time: Timespan, track: Track, segment: Segment): TimedFrame = {
      if (nullAt(time)) null else new TimedFrame(track.index, time)
    }

    override def getLengthPerExportFrame: Timespan = Timespan(1)

    override def getDuration: Timespan = Timespan(duration)

    override def getDefaultDuration: Option[Timespan] = Some(Timespan(duration))

    override def displayName: String = "probe"

    override def createTlSegmentActor(segment: Segment): TlSegmentActor = null
  }

  private class ProbeContent(duration: Long, canLead: Boolean, nullAt: Timespan => Boolean)
    extends Clip(TestIds.nextId(), new ProbeSource(duration, nullAt)) {

    def this(duration: Long, canLead: Boolean) = this(duration, canLead, _ => false)

    override def canCreateTransitionWith(other: Content): Boolean = canLead

    override def createTransition(other: Content)(using ids: IdSource): Transition = {
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
      new TimedFrame((if (fromFrame != null) fromFrame else toFrame).trackIndex, Timespan.MinValue)
    }

    override def displayName: String = "probe-transition"
  }

  /** 透传滤镜。 */
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

  /** 左右两块内容重叠出转场。 */
  private def placedTrack(leftLead: Boolean, leftNullAt: Timespan => Boolean, rightNullAt: Timespan => Boolean)
  : (Track, Transition, ProbeTransitionSource) = {
    val track = timeline.getTrackOrCreate(0)
    val left = new ProbeContent(1000, leftLead, leftNullAt)
    val right = new ProbeContent(1000, canLead = true, rightNullAt)
    timeline.add(track, left, at(0) ~~ at(1000), Instant.Zero)
    timeline.add(track, right, at(500) ~~ at(1500), at(500))
    val current = timeline.getTrackOrCreate(0)
    val transition = current.get(at(600)) match {
      case t: Transition => t
      case other => fail("重叠处应是转场，实际: " + other + "；轨道: " + current)
    }
    (current, transition, transition.source.asInstanceOf[ProbeTransitionSource])
  }

  test("mix 收到两侧各自的时间与进度") {
    val (track, transition, source) = placedTrack(leftLead = true, _ => false, _ => false)

    track.frameAt(transition, at(600))
    assertEquals(source.lastFrom.producedAt, span(600))
    assertEquals(source.lastTo.producedAt, span(100))
    assertEqualsFloat(source.lastProgress, 0.2f, 1e-6f)

    track.frameAt(transition, at(999))
    assertEquals(source.lastFrom.producedAt, span(999))
    assertEquals(source.lastTo.producedAt, span(499))
    assertEqualsFloat(source.lastProgress, 499f / 500f, 1e-6f)
  }

  test("产出时使用两侧的滤镜链") {
    val track = timeline.getTrackOrCreate(0)
    val left = new ProbeContent(1000, canLead = true)
    val right = new ProbeContent(1000, canLead = true)
    timeline.add(track, left, at(0) ~~ at(1000), Instant.Zero)
    timeline.add(track, right, at(500) ~~ at(1500), at(500))
    val current = timeline.getTrackOrCreate(0)
    val transition = current.get(at(600)) match {
      case t: Transition => t
      case other => fail("重叠处应是转场，实际: " + other + "；轨道: " + current)
    }
    val source = transition.source.asInstanceOf[ProbeTransitionSource]

    val filter = new CountingFilter
    left.attach(filter)
    current.frameAt(transition, at(600))

    assertEquals(filter.count, 1)
    assert((filter.lastFrame) eq (source.lastFrom))
  }

  test("翻转转场恢复两侧并反转进度") {
    val (track, transition, source) = placedTrack(leftLead = false, _ => false, _ => false)
    val right = track.get(at(1200)) match {
      case c: Content => c
      case _ => fail("转场终点之外应是右内容")
    }
    assert(source.from eq right.source)

    track.frameAt(transition, at(600))
    assertEquals(source.lastFrom.producedAt, span(600))
    assertEquals(source.lastTo.producedAt, span(100))
    assertEqualsFloat(source.lastProgress, 0.8f, 1e-6f)
  }

  test("一侧无帧时向 mix 传入空侧") {
    val (track, transition, source) = placedTrack(leftLead = true, _ == span(600), _ => false)

    track.frameAt(transition, at(600))
    assert((source.lastFrom) == null)
    assertEquals(source.lastTo.producedAt, span(100))
    assertEqualsFloat(source.lastProgress, 0.2f, 1e-6f)
  }

  test("sync 用换算后的时间到达两侧") {
    val (track, transition, _) = placedTrack(leftLead = true, _ => false, _ => false)
    val leftSource = track.get(at(200)).asInstanceOf[Content].source.asInstanceOf[ProbeSource]
    val rightSource = track.get(at(1200)).asInstanceOf[Content].source.asInstanceOf[ProbeSource]

    track.syncAt(transition, at(600))
    assertEquals(leftSource.lastSync, span(600))
    assertEquals(rightSource.lastSync, span(100))
  }

  test("翻转后 sync 正确转发") {
    val (track, transition, _) = placedTrack(leftLead = false, _ => false, _ => false)
    val leftSource = track.get(at(200)).asInstanceOf[Content].source.asInstanceOf[ProbeSource]
    val rightSource = track.get(at(1200)).asInstanceOf[Content].source.asInstanceOf[ProbeSource]

    track.syncAt(transition, at(600))
    assertEquals(leftSource.lastSync, span(600))
    assertEquals(rightSource.lastSync, span(100))
  }

  test("两侧都无帧时不产出帧") {
    val (track, transition, _) = placedTrack(leftLead = true, _ == span(600), _ == span(100))

    assert((track.frameAt(transition, at(600))) == null)
  }
}
