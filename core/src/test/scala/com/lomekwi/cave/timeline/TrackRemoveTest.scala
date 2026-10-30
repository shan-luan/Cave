package com.lomekwi.cave.timeline

import com.lomekwi.cave.project.{IdSource, TestProject}

/**
 * 删除路径的场景测试。内容与转场的删除在各种邻居与边界形态下的行为；
 * 移除后 [[TrackLayout]] 校验的布局不变量由本测试守护。
 */
class TrackRemoveTest extends GdxTestBase {

  private var project: TestProject = null
  private var timeline: TestTimeline = null

  given idSource: IdSource = () => project.context.idSource.nextId()

  override def beforeEach(context: BeforeEach): Unit = {
    project = new TestProject()
    timeline = project.timeline
  }

  private def newContent(duration: Long): Content = new TestCont(duration)

  private def place(track: Track, segment: Content, interval: Interval, origin: Long = 0L): Unit = {
    timeline.add(track, segment, interval, origin)
  }

  private def transitionOf(track: Track, time: Long): Transition = track.get(time) match {
    case t: Transition => t
    case _ => null
  }

  test("删除首个内容会分离其后的全部内容") {
    def t0: Track = timeline.getTrackOrCreate(0)
    val a = newContent(1000)
    val b = newContent(1000)
    place(t0, a, 0 ~~ 1000)
    place(t0, b, 500 ~~ 1500)
    assert((transitionOf(t0, 700)) != null)

    timeline.remove(a)

    val after = timeline.getTrackOrCreate(0)
    assertEquals(after.interval(b), 500 ~~ 1500)
    assert((transitionOf(after, 700)) == null)
    assert(after.get(300).isInstanceOf[Gap])
    TrackLayout.assertValid(after)
  }

  test("删除最后一个内容会缩短轨道长度") {
    def t0: Track = timeline.getTrackOrCreate(0)
    val a = newContent(1000)
    val b = newContent(1000)
    place(t0, a, 0 ~~ 1000)
    place(t0, b, 500 ~~ 1500)

    timeline.remove(b)

    val after = timeline.getTrackOrCreate(0)
    assertEquals(after.interval(a), 0 ~~ 1000)
    assertEquals(after.length, 1000L)
    assert((transitionOf(after, 700)) == null)
    assert(after.get(1200).isInstanceOf[Gap])
    TrackLayout.assertValid(after)
  }

  test("删除最后内容不影响独奏终点") {
    def t0: Track = timeline.getTrackOrCreate(0)
    val a = newContent(1000)
    val b = newContent(1000)
    place(t0, a, 0 ~~ 1000)
    place(t0, b, 500 ~~ 1500)

    timeline.remove(b)

    val after = timeline.getTrackOrCreate(0)
    assert(after.transitionAfter(a).isEmpty)
    assertEquals(after.soloEndOf(a), 1000L)
  }

  test("最后内容删除后转场在回归时可复用") {
    def t0: Track = timeline.getTrackOrCreate(0)
    val a = newContent(1000)
    val b = newContent(1000)
    place(t0, a, 0 ~~ 1000)
    place(t0, b, 500 ~~ 1500)
    val transition = transitionOf(t0, 700)
    assert((transition) != null)

    timeline.remove(b)
    assert(timeline.getTrackOrCreate(0).transitionAfter(a).isEmpty)

    val c = newContent(1000)
    place(timeline.getTrackOrCreate(0), c, 500 ~~ 1500)

    // 邻居回来后复用同一个转场对象，身份因此保持稳定
    val after = timeline.getTrackOrCreate(0)
    assertEquals(after.transitionAfter(a), Some(transition))
    TrackLayout.assertValid(after)
  }

  test("删除唯一内容后轨道为空") {
    val t0 = timeline.getTrackOrCreate(0)
    val a = newContent(1000)
    place(t0, a, 0 ~~ 1000)

    timeline.remove(a)

    val after = timeline.getTrackOrCreate(0)
    assert(after.isEmpty)
    assertEquals(after.length, 0L)
    assert(after.isEmpty)
  }

  test("删除中间内容会分离两侧邻居") {
    def t0: Track = timeline.getTrackOrCreate(0)
    val a = newContent(1000)
    val b = newContent(1000)
    val c = newContent(1000)
    place(t0, a, 0 ~~ 1000)
    place(t0, b, 500 ~~ 1500)
    place(t0, c, 1000 ~~ 2000)
    assert((transitionOf(t0, 700)) != null)
    assert((transitionOf(t0, 1200)) != null)

    timeline.remove(b)

    val after = timeline.getTrackOrCreate(0)
    assertEquals(after.interval(a), 0 ~~ 1000)
    assertEquals(after.interval(c), 1000 ~~ 2000)
    assert((a) eq (after.get(700)))
    assert((c) eq (after.get(1200)))
    assert(after.transitionBetween(a, c).isEmpty)
    TrackLayout.assertValid(after)
  }

  test("删除不属于该轨道的片段是空操作") {
    val t0 = timeline.getTrackOrCreate(0)
    val a = newContent(1000)
    val foreign = newContent(1000)
    place(t0, a, 0 ~~ 1000)

    val current = timeline.getTrackOrCreate(0)
    assert((current) eq (current.remove(foreign)))
  }

  test("删除内容不影响无关转场的对象身份") {
    def t0: Track = timeline.getTrackOrCreate(0)
    val a = newContent(1000)
    val b = newContent(1000)
    val c = newContent(1000)
    place(t0, a, 0 ~~ 1000)
    place(t0, b, 500 ~~ 1500)
    place(t0, c, 2000 ~~ 3000)
    val transition = transitionOf(t0, 700)
    assert((transition) != null)

    timeline.remove(c)

    val after = timeline.getTrackOrCreate(0)
    assert((transition) eq (transitionOf(after, 700)))
    TrackLayout.assertValid(after)
  }

  test("删除内容后空隙可复用") {
    def t0: Track = timeline.getTrackOrCreate(0)
    val a = newContent(1000)
    val b = newContent(1000)
    place(t0, a, 0 ~~ 1000)
    place(t0, b, 2000 ~~ 3000)

    timeline.remove(a)
    val c = newContent(1000)
    place(timeline.getTrackOrCreate(0), c, 500 ~~ 1500)

    val after = timeline.getTrackOrCreate(0)
    assertEquals(after.interval(c), 500 ~~ 1500)
    assert(after.get(100).isInstanceOf[Gap])
    TrackLayout.assertValid(after)
  }

  test("批量删除内容时逐个从轨道分离") {
    def t0: Track = timeline.getTrackOrCreate(0)
    val a = newContent(1000)
    val b = newContent(1000)
    val c = newContent(1000)
    place(t0, a, 0 ~~ 1000)
    place(t0, b, 500 ~~ 1500)
    place(t0, c, 2000 ~~ 3000)

    timeline.remove(Seq(a, c))

    val after = timeline.getTrackOrCreate(0)
    val remaining = TrackLayout.contentsOf(after)
    assertEquals(remaining.size, 1)
    assert((b) eq (remaining.head))
    assertEquals(after.interval(b), 500 ~~ 1500)
    assert(after.get(2500).isInstanceOf[Gap])
    TrackLayout.assertValid(after)
  }

  test("删除奇数重叠的转场时按下取整对半分割") {
    def t0: Track = timeline.getTrackOrCreate(0)
    val a = newContent(1000)
    val b = newContent(1000)
    place(t0, a, 0 ~~ 1000)
    place(t0, b, 989 ~~ 1500)
    val transition = transitionOf(t0, 995)
    assert((transition) != null)
    assertEquals(t0.interval(transition), 989 ~~ 1000)

    timeline.remove(transition)

    val after = timeline.getTrackOrCreate(0)
    assertEquals(after.interval(a), 0 ~~ 995)
    assertEquals(after.interval(b), 995 ~~ 1500)
    assert(after.transitionBetween(a, b).isEmpty)
    TrackLayout.assertValid(after)
  }

  test("删除最小重叠转场时把右侧内容移到左端") {
    def t0: Track = timeline.getTrackOrCreate(0)
    val a = newContent(1000)
    val b = newContent(1000)
    place(t0, a, 0 ~~ 1000)
    place(t0, b, 999 ~~ 1500)
    val transition = transitionOf(t0, 999)
    assert((transition) != null)
    assertEquals(t0.interval(transition), 999 ~~ 1000)

    timeline.remove(transition)

    val after = timeline.getTrackOrCreate(0)
    assertEquals(after.interval(a), 0 ~~ 1000)
    assertEquals(after.interval(b), 1000 ~~ 1500)
    assert(after.transitionBetween(a, b).isEmpty)
    TrackLayout.assertValid(after)
  }

  test("删除转场不影响左侧邻居的转场") {
    def t0: Track = timeline.getTrackOrCreate(0)
    val p = newContent(1000)
    val a = newContent(1000)
    val b = newContent(1000)
    place(t0, p, 0 ~~ 999)
    place(t0, a, 100 ~~ 1000)
    place(t0, b, 999 ~~ 1100)
    val transition = transitionOf(t0, 999)
    assert((transition) != null)

    timeline.remove(transition)

    val after = timeline.getTrackOrCreate(0)
    assertEquals(after.interval(a), 100 ~~ 1000)
    assertEquals(after.interval(b), 1000 ~~ 1100)
    assert((transitionOf(after, 500)) != null)
    TrackLayout.assertValid(after)
  }

  test("删除转场时在第二个后继前停止") {
    def t0: Track = timeline.getTrackOrCreate(0)
    val a = newContent(1000)
    val b = newContent(1000)
    val rn = newContent(1000)
    place(t0, a, 1000 ~~ 1101)
    place(t0, b, 1100 ~~ 1500)
    place(t0, rn, 1101 ~~ 2000)
    val transition = transitionOf(t0, 1100)
    assert((transition) != null)

    timeline.remove(transition)

    val after = timeline.getTrackOrCreate(0)
    assertEquals(after.interval(a), 1000 ~~ 1100)
    assertEquals(after.interval(b), 1100 ~~ 1500)
    assertEquals(after.interval(rn), 1101 ~~ 2000)
    assert((transitionOf(after, 1200)) != null)
    TrackLayout.assertValid(after)
  }

  test("被第二个后继挤压时删除转场会回滚") {
    def t0: Track = timeline.getTrackOrCreate(0)
    val p = newContent(1000)
    val a = newContent(1000)
    val b = newContent(1000)
    val rn = newContent(1000)
    place(t0, p, 800 ~~ 1100)
    place(t0, a, 1000 ~~ 1101)
    place(t0, b, 1100 ~~ 1500)
    place(t0, rn, 1101 ~~ 2000)
    val transition = transitionOf(t0, 1100)
    assert((transition) != null)

    timeline.remove(transition)

    val after = timeline.getTrackOrCreate(0)
    assertEquals(after.interval(a), 1000 ~~ 1101)
    assertEquals(after.interval(p), 800 ~~ 1100)
    assertEquals(after.interval(b), 1100 ~~ 1500)
    assertEquals(after.interval(rn), 1101 ~~ 2000)
    assert((transition) eq (transitionOf(after, 1100)))
    TrackLayout.assertValid(after)
  }

  test("删除转场后所有内容仍然可见") {
    for (rnLo <- 1101L to 1104L) {
      val tl = new TestProject().timeline
      val p = newContent(1000)
      val a = newContent(1000)
      val b = newContent(1000)
      val rn = newContent(1000)
      tl.add(tl.getTrackOrCreate(0), p, 800 ~~ 1100, 0L)
      tl.add(tl.getTrackOrCreate(0), a, 1000 ~~ 1101, 0L)
      tl.add(tl.getTrackOrCreate(0), b, 1100 ~~ 1500, 0L)
      tl.add(tl.getTrackOrCreate(0), rn, rnLo ~~ (rnLo + 1000), 0L)
      val transition = tl.getTrackOrCreate(0).get(1100) match {
        case t: Transition => t
        case _ => null
      }
      assert((transition) != null)

      tl.remove(transition)

      val after = tl.getTrackOrCreate(0)
      for (s <- Vector(p, a, b, rn)) {
        if (after.contains(s) != after.toVector.contains(s.id)) {
          fail("内容在轨道反查里存在却从迭代中消失: " + s + " rnLo=" + rnLo)
        }
      }
      TrackLayout.assertValid(after)
    }
  }

  test("批量删除转场时分割两处重叠") {
    def t0: Track = timeline.getTrackOrCreate(0)
    val a = newContent(1000)
    val b = newContent(1000)
    val c = newContent(1000)
    place(t0, a, 0 ~~ 1000)
    place(t0, b, 500 ~~ 1500)
    place(t0, c, 1000 ~~ 2000)
    val ab = transitionOf(t0, 700)
    val bc = transitionOf(t0, 1200)
    assert((ab) != null)
    assert((bc) != null)

    timeline.remove(Seq(ab, bc))

    val after = timeline.getTrackOrCreate(0)
    assertEquals(after.interval(a), 0 ~~ 750)
    assertEquals(after.interval(b), 750 ~~ 1250)
    assertEquals(after.interval(c), 1250 ~~ 2000)
    TrackLayout.assertValid(after)
  }

  test("删除已删除的转场是空操作") {
    def t0: Track = timeline.getTrackOrCreate(0)
    val a = newContent(1000)
    val b = newContent(1000)
    place(t0, a, 0 ~~ 1000)
    place(t0, b, 500 ~~ 1500)
    val transition = transitionOf(t0, 700)
    assert((transition) != null)

    timeline.remove(transition)

    val current = timeline.getTrackOrCreate(0)
    assert((current) eq (current.remove(transition)))
  }
}
