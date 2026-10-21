package com.lomekwi.cave.timeline

import com.lomekwi.cave.project.TestProject

/**
 * 转场拓扑的模型级测试。内容保留覆盖完整的区间，转场是相邻两块内容的重叠区，
 * 三者共存于同一张按区间索引的表里。
 */
class TrackTransitionTest extends GdxTestBase {

  private var project: TestProject = null
  private var timeline: TestTimeline = null

  override def beforeEach(context: BeforeEach): Unit = {
    project = new TestProject()
    timeline = project.timeline
  }

  private def newSegment(duration: Long): Content = new TestCont(duration)

  private def place(track: Track, segment: Content, interval: Interval, origin: Long = 0L): Unit = {
    timeline.addOrThrow(track, segment, interval, origin)
  }

  private def transitionOf(track: Track, time: Long): Transition = track.get(time) match {
    case t: Transition => t
    case _ => null
  }

  /** 不能构造转场的内容，createTransition 落到它身上即失败，用来验证兜底走向。 */
  private class NoTransitionCont(duration: Long) extends TestCont(duration) {
    override def canCreateTransitionWith(other: Content): Boolean = false

    override def createTransition(other: Content): Transition =
      throw new UnsupportedOperationException("转场不应由本片段构造")
  }

  test("重叠内容保留完整区间并共享转场") {
    def t0: Track = timeline.getTrackOrCreate(0)
    val a = newSegment(1000)
    val b = newSegment(1000)
    place(t0, a, 0 ~~ 1000)
    place(t0, b, 500 ~~ 1500)

    // 两侧内容都保留自己的完整区间，转场单独占据交集
    assertEquals(t0.getInterval(a), 0 ~~ 1000)
    assertEquals(t0.getInterval(b), 500 ~~ 1500)
    assertEquals(t0.getInterval(transitionOf(t0, 600)), 500 ~~ 1000)
  }

  test("get 按内容-转场-内容遍历") {
    def t0: Track = timeline.getTrackOrCreate(0)
    val a = newSegment(1000)
    val b = newSegment(1000)
    place(t0, a, 0 ~~ 1000)
    place(t0, b, 500 ~~ 1500)
    val transition = transitionOf(t0, 600)
    assert((transition) != null)

    assert((a) eq (t0.get(499)))
    assert((transition) eq (t0.get(500)))
    assert((transition) eq (t0.get(999)))
    assert((b) eq (t0.get(1000)))
    assert((b) eq (t0.get(1499)))
    assert(t0.get(1500).isInstanceOf[Gap])
  }

  test("intervalAt 优先返回转场而非覆盖它的内容") {
    def t0: Track = timeline.getTrackOrCreate(0)
    val a = newSegment(1000)
    val b = newSegment(1000)
    place(t0, a, 0 ~~ 1000)
    place(t0, b, 500 ~~ 1500)

    assertEquals(t0.intervalAt(100), 0 ~~ 1000)
    assertEquals(t0.intervalAt(600), 500 ~~ 1000)
    assertEquals(t0.intervalAt(1200), 500 ~~ 1500)
  }

  test("三方重叠被拒绝") {
    def t0: Track = timeline.getTrackOrCreate(0)
    val a = newSegment(1000)
    val b = newSegment(1000)
    val c = newSegment(1000)
    place(t0, a, 0 ~~ 1000)
    place(t0, b, 500 ~~ 1500)

    // c 放在 [800,1800) 会与 a 和 b 同时重叠，隔项不重叠被破坏
    intercept[IllegalArgumentException] { place(t0, c, 800 ~~ 1800) }
    assertEquals(t0.size, 3)
  }

  test("重叠不能吞并邻居") {
    def t0: Track = timeline.getTrackOrCreate(0)
    val a = newSegment(100000)
    val b = newSegment(1000)
    place(t0, a, 0 ~~ 1000)
    place(t0, b, 500 ~~ 1500)

    val applied: Long = timeline.setEnd(Seq(a), 100000)

    assertEquals(applied, 499L)
    assertEquals(t0.getInterval(a), 0 ~~ 1499)
    assertEquals(t0.getInterval(b), 500 ~~ 1500)
  }

  test("终点扩展在第二个后继前停止") {
    def t0: Track = timeline.getTrackOrCreate(0)
    val a = newSegment(100000)
    val b = newSegment(100000)
    val c = newSegment(100000)
    place(t0, a, 0 ~~ 1000)
    place(t0, b, 500 ~~ 2000)
    place(t0, c, 1500 ~~ 2500)

    val applied: Long = timeline.setEnd(Seq(a), 100000)

    assertEquals(applied, 500L)
    assertEquals(t0.getInterval(a), 0 ~~ 1500)
  }

  test("未受影响的转场保持对象身份") {
    def t0: Track = timeline.getTrackOrCreate(0)
    val a = newSegment(1000)
    val b = newSegment(1000)
    val c = newSegment(1000)
    place(t0, a, 0 ~~ 1000)
    place(t0, b, 500 ~~ 1500)
    place(t0, c, 2000 ~~ 3000)
    val transition = transitionOf(t0, 600)
    assert((transition) != null)

    // 平移远处的内容，a 与 b 之间的转场不受影响，应当复用同一个对象
    timeline.moveTime(Seq(c), 100)

    val after = timeline.getTrackOrCreate(0)
    assertEquals(after.getInterval(c), 2100 ~~ 3100)
    assert((transition) eq (transitionOf(after, 600)))
  }

  test("删除转场时把重叠区对半分割") {
    def t0: Track = timeline.getTrackOrCreate(0)
    val a = newSegment(1000)
    val b = newSegment(1000)
    place(t0, a, 0 ~~ 1000)
    place(t0, b, 500 ~~ 1500)
    val transition = transitionOf(t0, 600)
    assert((transition) != null)

    timeline.remove(transition)

    val after = timeline.getTrackOrCreate(0)
    assertEquals(after.getInterval(a), 0 ~~ 750)
    assertEquals(after.getInterval(b), 750 ~~ 1500)
    assert((transitionOf(after, 700)) == null)
  }

  test("删除内容不影响两侧邻居") {
    def t0: Track = timeline.getTrackOrCreate(0)
    val a = newSegment(1000)
    val b = newSegment(1000)
    val c = newSegment(1000)
    place(t0, a, 0 ~~ 1000)
    place(t0, b, 1500 ~~ 2500)
    place(t0, c, 2000 ~~ 3000) // 与 b 重叠

    timeline.remove(b)

    val after = timeline.getTrackOrCreate(0)
    assertEquals(after.getInterval(a), 0 ~~ 1000)
    assertEquals(after.getInterval(c), 2000 ~~ 3000)
    assert((transitionOf(after, 600)) == null)
  }

  test("收缩内容不能落入左邻居内部") {
    def t0: Track = timeline.getTrackOrCreate(0)
    val a = newSegment(100000)
    val c = newSegment(100000)
    val b = newSegment(100000)
    place(t0, a, 0 ~~ 1000)
    place(t0, c, 500 ~~ 2000)
    place(t0, b, 1200 ~~ 2500)

    // c 缩短到落进 a 内部时会变成 a 的真子集，终点必须留在 a 的终点之后
    val applied: Long = timeline.setEnd(Seq(c), -100000)

    assertEquals(applied, -999L)
    assertEquals(t0.getInterval(c), 500 ~~ 1001)
    assertEquals(t0.getInterval(a), 0 ~~ 1000)
  }

  test("左侧无法创建转场时回退到右侧") {
    def t0: Track = timeline.getTrackOrCreate(0)
    val a = new NoTransitionCont(1000)
    val b = newSegment(1000)
    place(t0, a, 0 ~~ 1000)
    place(t0, b, 500 ~~ 1500)

    // 前段不能构造，兜底由后段构造，重叠照常成为转场
    assert((transitionOf(t0, 600)) != null)
  }

  test("右侧无法创建转场时仍使用左侧") {
    def t0: Track = timeline.getTrackOrCreate(0)
    val a = newSegment(1000)
    val b = new NoTransitionCont(1000)
    place(t0, a, 0 ~~ 1000)
    place(t0, b, 500 ~~ 1500)

    // 前段能构造时仍走前段，后段不能构造不构成障碍
    assert((transitionOf(t0, 600)) != null)
  }

  test("两侧都无法创建转场时重叠被拒绝") {
    def t0: Track = timeline.getTrackOrCreate(0)
    val a = new NoTransitionCont(1000)
    val b = new NoTransitionCont(1000)
    place(t0, a, 0 ~~ 1000)

    intercept[IllegalArgumentException] { place(t0, b, 500 ~~ 1500) }
    assertEquals(t0.size, 1)
  }
}
