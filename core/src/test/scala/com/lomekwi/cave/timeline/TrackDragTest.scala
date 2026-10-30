package com.lomekwi.cave.timeline

import com.lomekwi.cave.project.{TestIds, TestProject}
import com.lomekwi.cave.project.UndoManager

import scala.collection.mutable

import com.lomekwi.cave.pipeline.Source
import com.lomekwi.cave.ui.editpanel.tlarea.TlSegmentActor

import scala.util.Using

/**
 * 拖拽 API 的模型级测试，验证截断语义：一次调用就把 deltaTime 同向截到最大可用偏移量并应用，
 * 返回值是实际应用的偏移量，0 表示未移动。
 */
class TrackDragTest extends GdxTestBase {

  private var project: TestProject = null
  private var timeline: TestTimeline = null

  override def beforeEach(context: BeforeEach): Unit = {
    project = new TestProject()
    timeline = project.timeline
  }

  private def newSegment(duration: Long): Content = {
    new TestCont(duration)
  }

  private def place(track: Track, segment: Content, interval: Interval): Unit = {
    timeline.add(track, segment, interval, 0L)
  }

  private def placeAt(track: Track, segment: Content, interval: Interval, origin: Long): Unit = {
    timeline.add(track, segment, interval, origin)
  }

  /** 无界测试片段，与 [[TestCont]] 相对，用于验证无界内容的前边缘约束。 */
  private class TestUnbounded extends Boundless(TestIds.nextId(), new UnbSource)

  private class UnbSource extends Source[TestCont.TestFrame] {
    override protected def produce(time: Long, track: Track, segment: Segment): TestCont.TestFrame = {
      new TestCont.TestFrame(track.index)
    }

    override def getLengthPerExportFrame: Long = {
      1
    }

    override def getDuration: Long = {
      Long.MaxValue
    }

    override def getDefaultDuration: Option[Long] = {
      None
    }

    override def displayName: String = {
      "test-unbounded"
    }

    override def createTlSegmentActor(segment: Segment): TlSegmentActor = {
      null
    }
  }

  /** 该时间点上的片段；落在空隙或轨道外时为 null。 */
  private def srcAt(track: Track, time: Long): Segment = track.get(time) match {
    case s: Segment => s
    case _: Gap => null
  }

  test("组可以包含转场") {
    def t0: Track = timeline.getTrackOrCreate(0)
    val a = newSegment(1000_000L)
    val b = newSegment(1000_000L)
    place(t0, a, 0L ~~ 1000_000L)
    place(t0, b, 800_000L ~~ 1800_000L)

    val tr = t0.transitionBetween(a.asInstanceOf[Content], b.asInstanceOf[Content]).getOrElse(fail("a 与 b 之间应有转场"))

    var group = timeline.newGroup()
    group = timeline.groupAdd(group, a)
    group = timeline.groupAdd(group, tr)
    group = timeline.groupAdd(group, b)
    assertEquals(group.size, 3)
    assertEquals(timeline.getGroup(tr), Some(group))
  }

  test("移动含转场的组不会重复位移转场") {
    def t0: Track = timeline.getTrackOrCreate(0)
    val a = newSegment(1000_000L)
    val b = newSegment(1000_000L)
    place(t0, a, 0L ~~ 1000_000L)
    place(t0, b, 800_000L ~~ 1800_000L)

    val tr = t0.transitionBetween(a.asInstanceOf[Content], b.asInstanceOf[Content]).getOrElse(fail("a 与 b 之间应有转场"))
    assertEquals(t0.interval(tr), 800_000L ~~ 1000_000L)

    val members = scala.collection.mutable.ArrayBuffer.empty[Segment]
    members += a
    members += tr
    members += b
    assertEquals(timeline.moveTime(members, 100_000L), 100_000L)

    assertEquals(t0.interval(a), 100_000L ~~ 1100_000L)
    assertEquals(t0.interval(b), 900_000L ~~ 1900_000L)
    assertEquals(t0.interval(tr), 900_000L ~~ 1100_000L)
  }

  test("组前移在第二个后继处停止") {
    def t0: Track = timeline.getTrackOrCreate(0)
    val x: Content = newSegment(1000_000L)
    val y: Content = newSegment(1000_000L)
    val n: Content = newSegment(1000_000L)
    place(t0, x, TrackDragTest.interval(0, 1000))
    place(t0, y, TrackDragTest.interval(500, 1500))
    place(t0, n, TrackDragTest.interval(1000, 2000))

    assertEquals(timeline.moveTime(Seq(x, y), 100), 0L)
    assertEquals(t0.interval(x), TrackDragTest.interval(0, 1000))
    assertEquals(t0.interval(y), TrackDragTest.interval(500, 1500))
    assertEquals(t0.interval(n), TrackDragTest.interval(1000, 2000))
  }

  test("组后移在第二个前驱处停止") {
    def t0: Track = timeline.getTrackOrCreate(0)
    val p: Content = newSegment(1000_000L)
    val x: Content = newSegment(1000_000L)
    val y: Content = newSegment(1000_000L)
    val n: Content = newSegment(1000_000L)
    place(t0, p, TrackDragTest.interval(0, 1000))
    place(t0, x, TrackDragTest.interval(1000, 2000))
    place(t0, y, TrackDragTest.interval(1500, 2500))
    place(t0, n, TrackDragTest.interval(2000, 3000))

    assertEquals(timeline.moveTime(Seq(x, y), -600), -500L)
    assertEquals(t0.interval(x), TrackDragTest.interval(500, 1500))
    assertEquals(t0.interval(y), TrackDragTest.interval(1000, 2000))
    assertEquals(t0.interval(p), TrackDragTest.interval(0, 1000))
  }

  test("组前移受近邻约束而非远处内容") {
    def t0: Track = timeline.getTrackOrCreate(0)
    val d: Content = newSegment(1000_000L)
    val a: Content = newSegment(1000_000L)
    val b: Content = newSegment(1000_000L)
    val c: Content = newSegment(1000_000L)
    val e: Content = newSegment(1000_000L)
    place(t0, d, TrackDragTest.interval(0, 3000))
    place(t0, a, TrackDragTest.interval(2000, 5000))
    place(t0, b, TrackDragTest.interval(4000, 7000))
    place(t0, c, TrackDragTest.interval(6000, 9000))
    place(t0, e, TrackDragTest.interval(8000, 11000))

    assertEquals(timeline.moveTime(Seq(a, b, c), 2000), 1000L)
    assertEquals(t0.interval(a), TrackDragTest.interval(3000, 6000))
    assertEquals(t0.interval(b), TrackDragTest.interval(5000, 8000))
    assertEquals(t0.interval(c), TrackDragTest.interval(7000, 10000))
    assertEquals(t0.interval(e), TrackDragTest.interval(8000, 11000))
  }

  test("移动组时组内转场对象身份保持") {
    def t0: Track = timeline.getTrackOrCreate(0)
    val d = newSegment(1000_000L)
    val a = newSegment(1000_000L)
    val b = newSegment(1000_000L)
    val c = newSegment(1000_000L)
    val e = newSegment(1000_000L)
    place(t0, d, TrackDragTest.interval(0, 3000))
    place(t0, a, TrackDragTest.interval(2000, 5000))
    place(t0, b, TrackDragTest.interval(4000, 7000))
    place(t0, c, TrackDragTest.interval(6000, 9000))
    place(t0, e, TrackDragTest.interval(8000, 11000))
    val ab = t0.transitionBetween(a.asInstanceOf[Content], b.asInstanceOf[Content]).getOrElse(fail("a 与 b 之间应有转场"))
    val bc = t0.transitionBetween(b.asInstanceOf[Content], c.asInstanceOf[Content]).getOrElse(fail("b 与 c 之间应有转场"))

    assertEquals(timeline.moveTime(Seq(a, b, c), 500L), 500L)

    val after = timeline.getTrackOrCreate(0)
    assertEquals(after.interval(a), TrackDragTest.interval(2500, 5500))
    assertEquals(after.interval(b), TrackDragTest.interval(4500, 7500))
    assertEquals(after.interval(c), TrackDragTest.interval(6500, 9500))
    // 组内相邻对的转场随两侧一起平移，应当仍是原来那两个对象
    assertEquals(after.transitionBetween(a.asInstanceOf[Content], b.asInstanceOf[Content]), Some(ab))
    assertEquals(after.transitionBetween(b.asInstanceOf[Content], c.asInstanceOf[Content]), Some(bc))
  }

  test("新轨道为空") {
    def t0: Track = timeline.getTrackOrCreate(0)

    assert(t0.isEmpty)
    assertEquals(t0.length, 0L)
    assert(srcAt(t0, 0) == null)
    assert(srcAt(t0, TrackDragTest.interval(0, 100).hi) == null)
  }

  test("片段区间可查询且空隙是隐式的") {
    def t0: Track = timeline.getTrackOrCreate(0)
    val s: Content = newSegment(100)
    place(t0, s, TrackDragTest.interval(0, 100))

    assertEquals(t0.interval(s), TrackDragTest.interval(0, 100))

    assert(t0.get(100).isInstanceOf[Gap])
    assertEquals(t0.intervalAt(100), TrackDragTest.interval(100, Long.MaxValue))
  }

  test("删除片段后合并两侧空隙") {
    def t0: Track = timeline.getTrackOrCreate(0)
    val a: Content = newSegment(100)
    val b: Content = newSegment(100)
    place(t0, a, TrackDragTest.interval(0, 100))
    place(t0, b, TrackDragTest.interval(200, 300))

    timeline.remove(a)
    assert(srcAt(t0, 50) == null)
    assertEquals(t0.intervalAt(50), TrackDragTest.interval(0, 200))
    assertEquals(t0.intervalAt(250), TrackDragTest.interval(200, 300))
  }

  test("添加片段时设置区间与轨道") {
    def t0: Track = timeline.getTrackOrCreate(0)
    val s: Content = newSegment(100)
    place(t0, s, TrackDragTest.interval(0, 100))

    assert((s) eq (srcAt(t0, 50)))
    assertEquals(t0.interval(s), TrackDragTest.interval(0, 100))
    assertEquals(timeline.findTrackOf(s), Some(t0))
  }

  test("在已占用区间 add 不会替换") {
    def t0: Track = timeline.getTrackOrCreate(0)
    val a: Content = newSegment(100)
    val b: Content = newSegment(100)
    place(t0, a, TrackDragTest.interval(0, 100))

    assert(!timeline.add(t0, b, TrackDragTest.interval(0, 100), 0L))

    assert((a) eq (srcAt(t0, 50)))
    assertEquals(countOn(t0), 1)
  }

  test("删除片段会将其移除") {
    def t0: Track = timeline.getTrackOrCreate(0)
    val s: Content = newSegment(100)
    place(t0, s, TrackDragTest.interval(0, 100))

    timeline.remove(s)
    assert(srcAt(t0, 50) == null)
    assert(t0.isEmpty)
  }

  test("批量删除集合会移除全部") {
    def t0: Track = timeline.getTrackOrCreate(0)
    val a: Content = newSegment(100)
    val b: Content = newSegment(100)
    place(t0, a, TrackDragTest.interval(0, 100))
    place(t0, b, TrackDragTest.interval(500, 600))

    timeline.remove(Seq(a, b))
    assert(t0.isEmpty)
  }

  test("删除片段会将其从组中摘除") {
    def t0: Track = timeline.getTrackOrCreate(0)
    val a: Content = newSegment(100)
    val b: Content = newSegment(100)
    place(t0, a, TrackDragTest.interval(0, 100))
    place(t0, b, TrackDragTest.interval(500, 600))
    var group: SegmentGroup = timeline.newGroup()
    group = timeline.groupAdd(group, a)
    group = timeline.groupAdd(group, b)

    Using.resource(timeline.record()) { _ =>
      timeline.remove(a)
    }

    assert(timeline.findTrackOf(a).isEmpty)
    val remaining = timeline.getGroup(b).orNull
    assert(remaining != null)
    assert(!remaining.contains(a.id))
    assert(remaining.contains(b.id))

    project.undo.undo()
    assertEquals(timeline.getGroup(a), Some(group))
    assertEquals(timeline.getGroup(b), Some(group))

    project.undo.redo()
    assert(!timeline.getGroup(b).orNull.contains(a.id))
  }

  test("删除片段会更新轨道长度") {
    def t0: Track = timeline.getTrackOrCreate(0)
    val a: Content = newSegment(100)
    val b: Content = newSegment(100)
    place(t0, a, TrackDragTest.interval(0, 100))
    place(t0, b, TrackDragTest.interval(500, 600))
    assertEquals(t0.length, 600L)

    timeline.remove(b)
    assertEquals(t0.length, 100L)
  }

  test("时间轴长度随编辑更新") {
    def t0: Track = timeline.getTrackOrCreate(0)
    val s: Content = newSegment(100)
    place(t0, s, TrackDragTest.interval(0, 100))
    assertEquals(timeline.getLength, 100L)

    timeline.moveTime(Seq(s), 900)
    assertEquals(timeline.getLength, 1000L)

    timeline.remove(s)
    assertEquals(timeline.getLength, 0L)
  }

  test("单片段按时间移动保持时长并偏移 origin") {
    def t0: Track = timeline.getTrackOrCreate(0)
    val s: Content = newSegment(100)
    placeAt(t0, s, TrackDragTest.interval(0, 100), 1000)

    val applied: Long = timeline.moveTime(Seq(s), 200)

    assertEquals(applied, 200L)
    assertEquals(t0.interval(s), TrackDragTest.interval(200, 300))
    assertEquals(timeline.findTrackOf(s), Some(t0))
    assertEquals(t0.getOrigin(s), 1200L)
  }

  test("单片段可跨轨道移动") {
    def t0: Track = timeline.getTrackOrCreate(0)
    def t1: Track = timeline.getTrackOrCreate(1)
    val s: Content = newSegment(100)
    place(t0, s, TrackDragTest.interval(0, 100))

    val applied: Int = timeline.moveTrack(Seq(s), 1)

    assertEquals(applied, 1)
    assertEquals(timeline.findTrackOf(s), Some(t1))
    assertEquals(t1.interval(s), TrackDragTest.interval(0, 100))
    assert(t0.isEmpty)
    assert((s) eq (srcAt(t1, 50)))
  }

  test("多选移动时所有成员一起移动") {
    def t0: Track = timeline.getTrackOrCreate(0)
    def t1: Track = timeline.getTrackOrCreate(1)
    val a: Content = newSegment(100)
    val b: Content = newSegment(100)
    place(t0, a, TrackDragTest.interval(0, 100))
    place(t1, b, TrackDragTest.interval(500, 600))

    val applied: Long = timeline.moveTime(Seq(a, b), 1000)

    assertEquals(applied, 1000L)
    assertEquals(t0.interval(a), TrackDragTest.interval(1000, 1100))
    assertEquals(t1.interval(b), TrackDragTest.interval(1500, 1600))
    assertEquals(timeline.findTrackOf(a), Some(t0))
    assertEquals(timeline.findTrackOf(b), Some(t1))
  }

  test("向前移动时前导成员被钳制在零") {
    def t0: Track = timeline.getTrackOrCreate(0)
    val a: Content = newSegment(100)
    val b: Content = newSegment(100)
    place(t0, a, TrackDragTest.interval(50, 150))
    place(t0, b, TrackDragTest.interval(200, 300))

    val applied: Long = timeline.moveTime(Seq(a, b), -200)

    assertEquals(applied, -50L)
    assertEquals(t0.interval(a), TrackDragTest.interval(0, 100))
    assertEquals(t0.interval(b), TrackDragTest.interval(150, 250))
  }

  test("移入已占用位置生成转场") {
    def t0: Track = timeline.getTrackOrCreate(0)
    val mover: Content = newSegment(100)
    val obstacle: Content = newSegment(1000)
    place(t0, mover, TrackDragTest.interval(0, 100))
    // 障碍占据 [100, 1100)，把 mover 挪到 [150,250) 会与它重叠
    place(t0, obstacle, TrackDragTest.interval(100, 1100))

    val applied: Long = timeline.moveTime(Seq(mover), 150)

    assertEquals(applied, 99L)
    // 内容保留完整区间，转场是两者的重叠区
    assertEquals(t0.interval(mover), TrackDragTest.interval(99, 199))
    val transition = srcAt(t0, 150)
    assert(transition.isInstanceOf[Transition])
    assertEquals(t0.interval(transition), TrackDragTest.interval(100, 199))
    assertEquals(t0.interval(obstacle), TrackDragTest.interval(100, 1100))
  }

  test("按时间移动与障碍重叠时建立转场") {
    def t0: Track = timeline.getTrackOrCreate(0)
    val mover: Content = newSegment(100)
    val obstacle: Content = newSegment(100)
    place(t0, mover, TrackDragTest.interval(0, 100))
    place(t0, obstacle, TrackDragTest.interval(300, 400))

    val applied: Long = timeline.moveTime(Seq(mover), 250)
    assertEquals(applied, 250L)
    assertEquals(t0.interval(mover), TrackDragTest.interval(250, 350))
    val transition = srcAt(t0, 320)
    assert(transition.isInstanceOf[Transition])
    assertEquals(t0.interval(transition), TrackDragTest.interval(300, 350))
    assertEquals(t0.interval(obstacle), TrackDragTest.interval(300, 400))

    assertEquals(timeline.moveTime(Seq(mover), -50), -50L)
    assertEquals(t0.interval(mover), TrackDragTest.interval(200, 300))
    assertEquals(t0.interval(obstacle), TrackDragTest.interval(300, 400))
    assert(!(t0.exists(_.isInstanceOf[Transition])))
  }

  test("跨轨道移入已占用位置不会生效") {
    def t0: Track = timeline.getTrackOrCreate(0)
    def t1: Track = timeline.getTrackOrCreate(1)
    val mover: Content = newSegment(100)
    val obstacle: Content = newSegment(1000)
    place(t0, mover, TrackDragTest.interval(0, 100))
    place(t1, obstacle, TrackDragTest.interval(0, 1000))

    val applied: Int = timeline.moveTrack(Seq(mover), 1)

    assertEquals(applied, 0)
    assertEquals(timeline.findTrackOf(mover), Some(t0))
    assertEquals(t0.interval(mover), TrackDragTest.interval(0, 100))
    assert((obstacle) eq (srcAt(t1, 50)))
  }

  test("跨轨道移动保留与转场相邻片段的未绑定区间") {
    def t0: Track = timeline.getTrackOrCreate(0)
    def t1: Track = timeline.getTrackOrCreate(1)
    val a: Content = newSegment(100)
    val b: Content = newSegment(100)
    place(t0, a, TrackDragTest.interval(0, 100))
    // 与 a 重叠建转场：a=[0,100) t=[80,100) b=[80,180)，b 的 origin 是 80
    placeAt(t0, b, TrackDragTest.interval(80, 180), 80)
    assertEquals(t0.interval(b), TrackDragTest.interval(80, 180))
    assertEquals(t0.interval(srcAt(t0, 90)), TrackDragTest.interval(80, 100))

    val applied: Int = timeline.moveTrack(Seq(b), 1)

    assertEquals(applied, 1)
    // 搬运的是 b 的完整区间，origin 不变
    assertEquals(t1.interval(b), TrackDragTest.interval(80, 180))
    assertEquals(t1.getOrigin(b), 80L)
    assertEquals(t0.interval(a), TrackDragTest.interval(0, 100))
    assert(!(t0.exists(_.isInstanceOf[Transition])))
  }

  test("设置起点时前缘滑动且终点固定") {
    def t0: Track = timeline.getTrackOrCreate(0)
    val s: Content = newSegment(100)
    place(t0, s, TrackDragTest.interval(0, 100))

    val applied: Long = timeline.setStart(Seq(s), 30)

    assertEquals(applied, 30L)
    assertEquals(t0.interval(s), TrackDragTest.interval(30, 100))
    assertEquals(timeline.findTrackOf(s), Some(t0))
    assertEquals(t0.getOrigin(s), 0L)
  }

  test("设置终点时后缘滑动且起点固定") {
    def t0: Track = timeline.getTrackOrCreate(0)
    val s: Content = newSegment(100)
    place(t0, s, TrackDragTest.interval(0, 50))

    val applied: Long = timeline.setEnd(Seq(s), 50)

    assertEquals(applied, 50L)
    assertEquals(t0.interval(s), TrackDragTest.interval(0, 100))
  }

  test("设置终点向后收缩") {
    def t0: Track = timeline.getTrackOrCreate(0)
    val s: Content = newSegment(100)
    place(t0, s, TrackDragTest.interval(0, 100))

    val applied: Long = timeline.setEnd(Seq(s), -20)

    assertEquals(applied, -20L)
    assertEquals(t0.interval(s), TrackDragTest.interval(0, 80))
  }

  test("设置起点进入障碍时生成转场") {
    def t0: Track = timeline.getTrackOrCreate(0)
    val s: Content = newSegment(100)
    val obstacle: Content = newSegment(100)
    place(t0, obstacle, TrackDragTest.interval(0, 100))
    place(t0, s, TrackDragTest.interval(100, 200))

    // 可建转场，起点最多左移到障碍终点前一微秒
    val applied: Long = timeline.setStart(Seq(s), -50)

    assertEquals(applied, -50L)
    assertEquals(t0.interval(s), TrackDragTest.interval(50, 200))
    assertEquals(t0.interval(obstacle), TrackDragTest.interval(0, 100))
    val transition = srcAt(t0, 75)
    assert(transition.isInstanceOf[Transition])
    assertEquals(t0.interval(transition), TrackDragTest.interval(50, 100))
  }

  test("向后设置起点时钳制到最近障碍边缘") {
    def t0: Track = timeline.getTrackOrCreate(0)
    val s: Content = newSegment(200)
    val obstacle: Content = newSegment(50)
    place(t0, s, TrackDragTest.interval(50, 100))      // origin 为 0，最小起点也是 0
    place(t0, obstacle, TrackDragTest.interval(0, 30))

    val applied: Long = timeline.setStart(Seq(s), -60)

    assertEquals(applied, -49L)
    assertEquals(t0.interval(s), TrackDragTest.interval(1, 100))
    assertEquals(t0.interval(obstacle), TrackDragTest.interval(0, 30))
    val transition = srcAt(t0, 10)
    assert(transition.isInstanceOf[Transition])
    assertEquals(t0.interval(transition), TrackDragTest.interval(1, 30))
  }

  test("向后设置起点受源起点钳制") {
    def t0: Track = timeline.getTrackOrCreate(0)
    val s: Content = newSegment(100)
    placeAt(t0, s, TrackDragTest.interval(100, 200), 100) // 素材起点即 origin

    // 素材前面没有内容，前边缘回退不到 origin 之前
    val applied: Long = timeline.setStart(Seq(s), -50)

    assertEquals(applied, 0L)
    assertEquals(t0.interval(s), TrackDragTest.interval(100, 200))
  }

  test("无界片段向后设置起点可到达时间轴起点") {
    def t0: Track = timeline.getTrackOrCreate(0)
    val s: Content = new TestUnbounded
    placeAt(t0, s, TrackDragTest.interval(100, 200), 100) // origin 是落点，无界内容不被它卡住

    // 没有素材起点，前边缘一直回退到时间轴 0
    val applied: Long = timeline.setStart(Seq(s), -150)

    assertEquals(applied, -100L)
    assertEquals(t0.interval(s), TrackDragTest.interval(0, 200))
  }

  test("向前设置终点时钳制到最近障碍边缘") {
    def t0: Track = timeline.getTrackOrCreate(0)
    val s: Content = newSegment(100)
    val obstacle: Content = newSegment(40)
    place(t0, s, TrackDragTest.interval(0, 50))
    place(t0, obstacle, TrackDragTest.interval(80, 120))

    val applied: Long = timeline.setEnd(Seq(s), 200)

    assertEquals(applied, 50L)
    assertEquals(t0.interval(s), TrackDragTest.interval(0, 100))
    assertEquals(t0.interval(obstacle), TrackDragTest.interval(80, 120))
    val transition = srcAt(t0, 90)
    assert(transition.isInstanceOf[Transition])
    assertEquals(t0.interval(transition), TrackDragTest.interval(80, 100))
  }

  test("设置组终点使相邻成员重叠") {
    def t0: Track = timeline.getTrackOrCreate(0)
    val a: Content = newSegment(100)
    val b: Content = newSegment(100)
    placeAt(t0, a, TrackDragTest.interval(0, 100), 500)   // 允许尾端伸展
    placeAt(t0, b, TrackDragTest.interval(100, 200), 600) // 与 a 相邻

    val applied: Long = timeline.setEnd(Seq(a, b), 50)
    assertEquals(applied, 50L)
    assertEquals(t0.interval(a), TrackDragTest.interval(0, 150))
    assertEquals(t0.interval(b), TrackDragTest.interval(100, 250))
    val transition = srcAt(t0, 125)
    assert(transition.isInstanceOf[Transition])
    assertEquals(t0.interval(transition), TrackDragTest.interval(100, 150))
  }

  test("设置组起点时相邻成员一起移动") {
    def t0: Track = timeline.getTrackOrCreate(0)
    val a: Content = newSegment(100)
    val b: Content = newSegment(100)
    place(t0, a, TrackDragTest.interval(100, 200))
    place(t0, b, TrackDragTest.interval(200, 300)) // 与 a 相邻

    val applied: Long = timeline.setStart(Seq(a, b), -50)
    assertEquals(applied, -50L)
    assertEquals(t0.interval(a), TrackDragTest.interval(50, 200))
    val transition = srcAt(t0, 175)
    assert(transition.isInstanceOf[Transition])
    assertEquals(t0.interval(transition), TrackDragTest.interval(150, 200))
    assertEquals(t0.interval(b), TrackDragTest.interval(150, 300))

    assertEquals(timeline.setStart(Seq(a, b), -50), -50L)
    assertEquals(t0.interval(a), TrackDragTest.interval(0, 200))
    val kept = srcAt(t0, 175)
    assert(kept.isInstanceOf[Transition])
    assertEquals(t0.interval(kept), TrackDragTest.interval(100, 200))
    assertEquals(t0.interval(b), TrackDragTest.interval(100, 300))

    assertEquals(timeline.setStart(Seq(a, b), -50), 0L)
    assertEquals(t0.interval(a), TrackDragTest.interval(0, 200))
    assertEquals(t0.interval(b), TrackDragTest.interval(100, 300))
  }

  test("阈值内吸附到最近边缘") {
    def t0: Track = timeline.getTrackOrCreate(0)
    val a: Content = newSegment(100)
    val b: Content = newSegment(100)
    place(t0, a, TrackDragTest.interval(100, 200))
    place(t0, b, TrackDragTest.interval(400, 500))

    assertEquals(timeline.snapTime(105, 10, Set.empty), 100L)
    assertEquals(timeline.snapTime(497, 10, Set.empty), 500L)
    assertEquals(timeline.snapTime(300, 10, Set.empty), 300L)
    assertEquals(timeline.snapTime(120, 10, Set.empty), 120L)
  }

  test("吸附忽略指定片段并吸附到零") {
    def t0: Track = timeline.getTrackOrCreate(0)
    val a: Content = newSegment(100)
    place(t0, a, TrackDragTest.interval(100, 200))

    assertEquals(timeline.snapTime(150, 10, Set(a)), 150L)
    assertEquals(timeline.snapTime(5, 10, Set.empty), 0L)
  }

  test("分割把片段切成区间正确的两半".ignore) {
    def t0: Track = timeline.getTrackOrCreate(0)
    val s: Content = newSegment(100)
    placeAt(t0, s, TrackDragTest.interval(0, 100), 1000)

    timeline.split(t0, 40)

    assertEquals(t0.interval(s), TrackDragTest.interval(0, 40))
    assert((s) eq (srcAt(t0, 20)))
    assertEquals(t0.getOrigin(s), 1000L)
    val right: Segment = srcAt(t0, 60)
    assert(!((s) eq (right)))
    assertEquals(t0.interval(right), TrackDragTest.interval(40, 100))
    assertEquals(timeline.findTrackOf(right), Some(t0))
    // 右半 origin 与左半一致，内容才接得上
    assertEquals(t0.getOrigin(right), 1000L)
    assertEquals(countOn(t0), 2)
  }

  test("撤销/重做移动恢复状态") {
    def t0: Track = timeline.getTrackOrCreate(0)
    val s: Content = newSegment(100)
    placeAt(t0, s, TrackDragTest.interval(0, 100), 1000)
    val before = timeline.timeline

    Using.resource(timeline.record()) { _ =>
      timeline.moveTrack(Seq(s), 1)
    }
    val after = timeline.timeline
    assertEquals(timeline.findTrackOf(s).orNull, timeline.getTrackOrCreate(1))

    project.undo.undo()
    assertEquals(timeline.timeline, before)
    assertEquals(timeline.findTrackOf(s), Some(t0))
    assertEquals(t0.getOrigin(s), 1000L)

    project.undo.redo()
    assertEquals(timeline.timeline, after)
  }

  test("撤销/重做缩放恢复状态") {
    def t0: Track = timeline.getTrackOrCreate(0)
    val s: Content = newSegment(100)
    place(t0, s, TrackDragTest.interval(0, 100))
    val before = timeline.timeline

    Using.resource(timeline.record()) { _ =>
      timeline.setEnd(Seq(s), -30)
    }
    assertEquals(t0.interval(s), TrackDragTest.interval(0, 70))

    project.undo.undo()
    assertEquals(timeline.timeline, before)
    assertEquals(t0.interval(s), TrackDragTest.interval(0, 100))

    project.undo.redo()
    assertEquals(t0.interval(s), TrackDragTest.interval(0, 70))
  }

  test("撤销/重做分割恢复状态".ignore) {
    def t0: Track = timeline.getTrackOrCreate(0)
    val s: Content = newSegment(100)
    val interval = TrackDragTest.interval(0, 100)
    place(t0, s, interval)
    val before = timeline.timeline.duplicate()

    Using.resource(timeline.record()) { _ =>
      timeline.split(t0, interval.lo + 40)
    }
    assertEquals(countOn(timeline.getTrackOrCreate(0)), 2)

    project.undo.undo()
    assertEquals(timeline.timeline, before)
    assertEquals(countOn(timeline.getTrackOrCreate(0)), 1)
    assert((s) eq (srcAt(timeline.getTrackOrCreate(0), interval.lo + 50)))

    project.undo.redo()
    assertEquals(countOn(timeline.getTrackOrCreate(0)), 2)
  }

  test("复合移动的撤销恢复多个片段") {
    def t0: Track = timeline.getTrackOrCreate(0)
    def t1: Track = timeline.getTrackOrCreate(1)
    val a: Content = newSegment(100)
    placeAt(t0, a, TrackDragTest.interval(0, 100), 1000)
    val b: Content = newSegment(100)
    placeAt(t1, b, TrackDragTest.interval(500, 600), 2000)

    // 模拟 UI 拖拽，record 期间直接改动模型，关闭时合并为一条复合命令
    Using.resource(timeline.record()) { h =>
      timeline.moveTime(Seq(a, b), 1000)
    }
    assertEquals(t0.interval(a), TrackDragTest.interval(1000, 1100))
    assertEquals(t0.getOrigin(a), 2000L)
    assertEquals(t1.interval(b), TrackDragTest.interval(1500, 1600))
    assertEquals(t1.getOrigin(b), 3000L)

    project.undo.undo()
    assertEquals(t0.interval(a), TrackDragTest.interval(0, 100))
    assertEquals(t0.getOrigin(a), 1000L)
    assertEquals(t1.interval(b), TrackDragTest.interval(500, 600))
    assertEquals(t1.getOrigin(b), 2000L)
  }

  private def countOn(track: Track): Int = track.size
}

object TrackDragTest {
  private def interval(lo: Long, hi: Long): Interval = {
    lo ~~ hi
  }
}
