package com.lomekwi.cave.ui.editpanel.tlarea

import com.lomekwi.cave.app.selection.SegmentSet
import com.lomekwi.cave.project.TestProject
import com.lomekwi.cave.timeline.{Content, GdxTestBase, Instant, Interval, Segment, TestCont, TestTimeline, Timespan, Timeline, Track, Transition}
import com.lomekwi.cave.timeline.~~

import org.objenesis.ObjenesisStd

import java.lang.reflect.Field

import scala.annotation.targetName

import TimelineViewDragSimTest.*

/**
 * 用真实 TlSegmentActor 拖拽会话模拟"鼠标拖拽"交互，验证 UI 拖拽逻辑。
 */
class TimelineViewDragSimTest extends GdxTestBase {

  private var project: TestProject = null
  private var timeline: TestTimeline = null
  private var tl: TimelineView = null
  private var view: TimelineView.ViewState = null

  override def beforeEach(context: BeforeEach): Unit = {
    project = new TestProject()
    timeline = project.timeline

    tl = new ObjenesisStd().newInstance(classOf[TimelineView])
    tl.setSize(WIDTH, HEIGHT)

    setField(tl, "session", project.session)
    setField(tl, "selectedSegments", new SegmentSet(project.session))
    setField(tl, "dirty", true)

    view = new TimelineView.ViewState()
    view.startTime = Instant.Zero
    view.durationTime = Timespan(DURATION_US)
    view.trackHeight = TRACK_H
    view.trackYShift = 0
    setField(tl, "view", view)
  }

  private def at(value: Long): Instant = Instant.At(Timespan(value))

  private def span(value: Long): Timespan = Timespan(value)

  extension (lo: Long)
    private def ~~(hi: Long): Interval = Interval(at(lo), at(hi))

  private def newSegment(duration: Long): Content = {
    new TestCont(duration)
  }

  private def absX(time: Instant): Float = {
    view.timeToX(time, tl.getWidth)
  }

  @targetName("absXLong")
  private def absX(time: Long): Float = absX(at(time))

  private def trackTopY(index: Int): Float = {
    tl.getHeight + view.trackYShift - (index + 1) * view.trackHeight
  }

  /** 在模型上放置一个片段，并按 [[TimelineView.act]] 的重建逻辑摆好 Actor。 */
  private def place(track: Track, segment: Content, start: Instant, end: Instant, origin: Instant): TlSegmentActor = {
    timeline.add(track, segment, Interval(start, end), origin)
    val current = timeline.findTrackOf(segment).getOrElse(track)
    val actor = segment.getTlSegmentActor
    actor.tl = tl
    actor.track = current
    actor.setPosition(absX(start), trackTopY(current.index))
    actor.setSize(absX(end) - absX(start), view.trackHeight)
    actor
  }

  @targetName("placeLong")
  private def place(track: Track, segment: Content, start: Long, end: Long, origin: Long): TlSegmentActor =
    place(track, segment, at(start), at(end), at(origin))

  /** 模拟 [[TimelineView.act]] 重建。 */
  private def rebuildFromModel(actor: TlSegmentActor): Unit = {
    val segment = actor.segment
    timeline.findTrackOf(segment).foreach { track =>
      actor.track = track
      val r = track.interval(segment)
      actor.setPosition(absX(r.lo), trackTopY(track.index))
      actor.setSize(absX(r.hi) - absX(r.lo), view.trackHeight)
    }
  }

  test("中部拖拽按相同增量移动且稳定可撤销") {
    def t0 = timeline.getTrackOrCreate(0)
    val s = newSegment(1000_000L)
    val actor = place(t0, s, 0, 1000_000L, 5_000_000L)

    val firstX = actor.getWidth / 2
    val firstY = view.trackHeight / 2
    actor.dragSide = DragSide.MIDDLE
    actor.initDrag(firstX, firstY)

    val mouseLocalX = actor.getX + firstX + 100f
    val mouseLocalY = actor.getY + firstY

    actor.dragTo(mouseLocalX, mouseLocalY)
    assertEquals(t0.interval(s), 100_000L ~~ (1000_000L + 100_000L))

    var i = 0
    while (i < 5) {
      rebuildFromModel(actor)
      assertEquals(t0.interval(s), 100_000L ~~ (1000_000L + 100_000L))
      assertEquals(t0.getOrigin(s), at(5_000_000L + 100_000L))
      i += 1
    }

    actor.finishDrag()

    project.undo.undo()
    assertEquals(t0.interval(s), 0L ~~ 1000_000L)
    assertEquals(t0.getOrigin(s), at(5_000_000L))
  }

  test("中部拖拽竖直落到鼠标所在轨道且稳定") {
    def t0 = timeline.getTrackOrCreate(0)
    timeline.getTrackOrCreate(3)
    val s = newSegment(1000_000L)
    val actor = place(t0, s, 0, 1000_000L, 0L)

    val firstX = actor.getWidth / 2
    val firstY = view.trackHeight / 2
    actor.dragSide = DragSide.MIDDLE
    actor.initDrag(firstX, firstY)

    val mouseLocalY = 200f
    val mouseLocalX = actor.getX + firstX
    actor.dragTo(mouseLocalX, mouseLocalY)

    assertEquals(timeline.findTrackOf(s).map(_.index), Some(2))

    var i = 0
    while (i < 5) {
      rebuildFromModel(actor)
      assertEquals(timeline.findTrackOf(s).map(_.index), Some(2))
      i += 1
    }
  }

  test("前缘缩放按相同增量移动起点、保持 origin 且稳定") {
    def t0 = timeline.getTrackOrCreate(0)
    val s = newSegment(1000_000L)
    val actor = place(t0, s, 0, 1000_000L, 0L)

    actor.dragSide = DragSide.FRONT
    actor.initDrag(5f, view.trackHeight / 2)

    val newFrontLocalX = 50f
    actor.dragTo(newFrontLocalX, view.trackHeight / 2)
    assertEquals(t0.interval(s), 50_000L ~~ 1000_000L)
    assertEquals(t0.getOrigin(s), at(0L))

    var i = 0
    while (i < 5) {
      rebuildFromModel(actor)
      assertEquals(t0.interval(s), 50_000L ~~ 1000_000L)
      assertEquals(t0.getOrigin(s), at(0L))
      i += 1
    }
  }

  test("后缘缩放按相同增量移动终点、保持 origin 且稳定") {
    def t0 = timeline.getTrackOrCreate(0)
    val s = newSegment(1000_000L)
    val actor = place(t0, s, 0, 1000_000L, 5_000_000L)

    actor.dragSide = DragSide.BEHIND
    actor.initDrag(actor.getWidth, view.trackHeight / 2)

    val newWidth = actor.getWidth + 60f
    actor.dragTo(newWidth, view.trackHeight / 2)
    assertEquals(t0.interval(s), 0L ~~ (1000_000L + 60_000L))
    assertEquals(t0.getOrigin(s), at(5_000_000L))

    var i = 0
    while (i < 5) {
      rebuildFromModel(actor)
      assertEquals(t0.interval(s), 0L ~~ (1000_000L + 60_000L))
      assertEquals(t0.getOrigin(s), at(5_000_000L))
      i += 1
    }
  }

  test("含转场成员的中部拖拽保持稳定") {
    def t0 = timeline.getTrackOrCreate(0)
    val a = newSegment(2000_000L)
    val b = newSegment(2000_000L)
    val aActor = place(t0, a, 0, 1000_000L, 0L)
    place(t0, b, 500_000L, 1500_000L, 500_000L)
    rebuildFromModel(aActor)

    val transition: Segment = t0.get(at(500_000)) match {
      case s: Segment => s
      case _ => null
    }
    assert(transition.isInstanceOf[Transition])

    tl.selectedSegments.add(a.id)
    tl.selectedSegments.add(transition.id)

    val firstX = aActor.getWidth / 2
    val firstY = view.trackHeight / 2
    aActor.dragSide = DragSide.MIDDLE
    aActor.initDrag(firstX, firstY)

    val mouseLocalX = aActor.getX + firstX + 100f
    val mouseLocalY = aActor.getY + firstY
    aActor.dragTo(mouseLocalX, mouseLocalY)

    assert(timeline.findTrackOf(transition).isDefined)

    var i = 0
    while (i < 5) {
      rebuildFromModel(aActor)
      aActor.dragTo(mouseLocalX, mouseLocalY)
      assert(timeline.findTrackOf(a).isDefined)
      i += 1
    }

    aActor.finishDrag()
    project.undo.undo()
    assertEquals(t0.interval(a), 0L ~~ 1000_000L)
    assert(timeline.findTrackOf(transition).isDefined)
  }

  test("以转场为锚点的中部拖拽保持稳定") {
    def t0 = timeline.getTrackOrCreate(0)
    val a = newSegment(2000_000L)
    val b = newSegment(2000_000L)
    place(t0, a, 0, 1000_000L, 0L)
    place(t0, b, 500_000L, 1500_000L, 500_000L)
    val transition: Segment = t0.get(at(500_000)) match {
      case s: Segment => s
      case _ => null
    }
    assert(transition.isInstanceOf[Transition])

    tl.selectedSegments.add(transition.id)
    tl.selectedSegments.add(b.id)

    val tActor = transition.getTlSegmentActor
    tActor.tl = tl
    rebuildFromModel(tActor)

    val firstX = tActor.getWidth / 2
    val firstY = view.trackHeight / 2
    tActor.dragSide = DragSide.MIDDLE
    tActor.initDrag(firstX, firstY)

    val mouseLocalX = tActor.getX + firstX + 100f
    val mouseLocalY = tActor.getY + firstY
    tActor.dragTo(mouseLocalX, mouseLocalY)

    assert(timeline.findTrackOf(transition).isDefined)

    var i = 0
    while (i < 5) {
      tActor.dragTo(mouseLocalX, mouseLocalY)
      i += 1
    }

    tActor.finishDrag()
    project.undo.undo()
    assert(timeline.findTrackOf(transition).isDefined)
  }

  test("中部拖拽吸附到重叠邻居边缘") {
    def t0 = timeline.getTrackOrCreate(0)
    val a = newSegment(1000_000L)
    val b = newSegment(1000_000L)
    val aActor = place(t0, a, 0, 1000_000L, 0L)
    place(t0, b, 500_000L, 1500_000L, 500_000L)
    rebuildFromModel(aActor)

    val firstX = aActor.getWidth / 2
    val firstY = view.trackHeight / 2
    aActor.dragSide = DragSide.MIDDLE
    aActor.initDrag(firstX, firstY)

    aActor.dragTo(absX(495_000L) + firstX, aActor.getY + firstY)

    assertEquals(tl.snapIndicatorTime, Some(at(500_000)))
    assert((t0.interval(a).lo - at(500_000)).abs <= span(1))
  }

  test("后缘拖拽越过转场删除后仍继续跟随") {
    def t0 = timeline.getTrackOrCreate(0)
    val a = newSegment(1000_000L)
    val b = newSegment(1000_000L)
    place(t0, a, 0, 1000_000L, 0L)
    place(t0, b, 500_000L, 1500_000L, 500_000L)
    val transition: Segment = t0.get(at(500_000)) match {
      case s: Segment => s
      case _ => null
    }
    assert(transition.isInstanceOf[Transition])

    val tActor = transition.getTlSegmentActor
    tActor.tl = tl
    rebuildFromModel(tActor)
    tActor.dragSide = DragSide.BEHIND
    tActor.initDrag(tActor.getWidth - 5f, view.trackHeight / 2)

    tActor.dragTo(absX(400_000L), view.trackHeight / 2)
    assert(timeline.findTrackOf(transition).isEmpty)
    assertEquals(t0.interval(a), 0L ~~ 400_000L)

    tActor.dragTo(absX(900_000L), view.trackHeight / 2)
    val rebuilt: Segment = t0.get(at(600_000)) match {
      case s: Segment => s
      case _ => null
    }
    assert(rebuilt.isInstanceOf[Transition])
    assertEquals(t0.interval(rebuilt), 500_000L ~~ 900_000L)

    tActor.dragTo(absX(896_000L), view.trackHeight / 2)
    assertEquals(t0.interval(a).hi, at(896_000))
    assertEquals(t0.interval(rebuilt), 500_000L ~~ 896_000L)
  }

  test("前缘拖拽越过转场会删除转场并继续跟随") {
    def t0 = timeline.getTrackOrCreate(0)
    val a = newSegment(1000_000L)
    val b = newSegment(1000_000L)
    val aActor = place(t0, a, 0, 1000_000L, 0L)
    place(t0, b, 500_000L, 1500_000L, 500_000L)
    assertEquals(t0.interval(a), 0L ~~ 1000_000L)
    assertEquals(t0.interval(b), 500_000L ~~ 1500_000L)
    rebuildFromModel(aActor)

    val transition: Segment = t0.get(at(500_000)) match {
      case s: Segment => s
      case _ => null
    }
    assert(transition.isInstanceOf[Transition])

    val tActor = transition.getTlSegmentActor
    tActor.tl = tl
    rebuildFromModel(tActor)

    tActor.dragSide = DragSide.FRONT
    tActor.initDrag(5f, view.trackHeight / 2)
    val mouseLocalX = absX(1200_000L)
    tActor.dragTo(mouseLocalX, view.trackHeight / 2)

    assert(timeline.findTrackOf(transition).isEmpty)
    assertEquals(t0.interval(a), 0L ~~ 1000_000L)
    val bInterval = t0.interval(b)
    assertEquals(bInterval.hi, at(1500_000))
    assert((bInterval.lo - at(1200_000)).abs <= span(1000), "b 起点应跟手到约 1200000，实际 " + bInterval.lo)

    tActor.finishDrag()
    project.undo.undo()
    assertEquals(t0.interval(a), 0L ~~ 1000_000L)
    assertEquals(t0.interval(b), 500_000L ~~ 1500_000L)
    assert(timeline.findTrackOf(transition).isDefined)
  }

  test("前缘拖拽越过空隙后重建转场") {
    def t0 = timeline.getTrackOrCreate(0)
    val a = newSegment(1000_000L)
    val b = newSegment(1000_000L)
    val aActor = place(t0, a, 0, 1000_000L, 0L)
    place(t0, b, 500_000L, 1500_000L, 500_000L)
    rebuildFromModel(aActor)

    val transition: Segment = t0.get(at(500_000)) match {
      case s: Segment => s
      case _ => null
    }
    val tActor = transition.getTlSegmentActor
    tActor.tl = tl
    rebuildFromModel(tActor)

    tActor.dragSide = DragSide.FRONT
    tActor.initDrag(5f, view.trackHeight / 2)
    tActor.dragTo(absX(1200_000L), view.trackHeight / 2)
    assert(timeline.findTrackOf(transition).isEmpty)

    tActor.dragTo(absX(900_000L), view.trackHeight / 2)
    assert(t0.get(at(900_000)).isInstanceOf[Transition])
    assertEquals(t0.interval(a), 0L ~~ 1000_000L)
    assertEquals(t0.interval(b), 900_000L ~~ 1500_000L)
  }

  test("后缘拖拽越过转场会删除转场并继续跟随") {
    def t0 = timeline.getTrackOrCreate(0)
    val a = newSegment(1000_000L)
    val b = newSegment(1000_000L)
    val aActor = place(t0, a, 0, 1000_000L, 0L)
    place(t0, b, 500_000L, 1500_000L, 500_000L)
    rebuildFromModel(aActor)

    val transition: Segment = t0.get(at(500_000)) match {
      case s: Segment => s
      case _ => null
    }
    val tActor = transition.getTlSegmentActor
    tActor.tl = tl
    rebuildFromModel(tActor)

    tActor.dragSide = DragSide.BEHIND
    tActor.initDrag(tActor.getWidth, view.trackHeight / 2)
    tActor.dragTo(absX(300_000L), view.trackHeight / 2)

    assert(timeline.findTrackOf(transition).isEmpty)
    assertEquals(t0.interval(a), 0L ~~ 300_000L)
    assertEquals(t0.interval(b), 500_000L ~~ 1500_000L)
  }
}

object TimelineViewDragSimTest {

  private val WIDTH: Float = 2000f
  private val HEIGHT: Float = 400f
  private val TRACK_H: Float = 80f
  private val DURATION_US: Long = 2_000_000L

  private def setField(target: Object, name: String, value: Any): Unit = {
    val f: Field = target.getClass.getDeclaredField(name)
    f.setAccessible(true)
    f.set(target, value.asInstanceOf[AnyRef])
  }
}
