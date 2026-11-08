package com.lomekwi.cave.timeline

import com.lomekwi.cave.project.{Id, TestProject}

class TrackIdTest extends GdxTestBase {

  private var project: TestProject = null
  private var timeline: TestTimeline = null

  private def at(value: Long): Instant = Instant.At(Timespan(value))

  override def beforeEach(context: BeforeEach): Unit = {
    project = new TestProject()
    timeline = project.timeline
  }

  test("block 在轨道 map 里但不参与遍历，maxId 忽略它") {
    val track = timeline.getTrackOrCreate(0)
    assert(track.segmentOption(Id.noId()).isDefined)
    assert(!track.iterator.contains(Id.noId()))
    assertEquals(timeline.timeline.maxId, Id.noId())
  }

  test("按 id 解析内容与转场") {
    val track = timeline.getTrackOrCreate(0)
    val a = new TestCont(1000)
    val b = new TestCont(1000)
    timeline.add(track, a, at(0) ~~ at(1000), Instant.Zero)
    timeline.add(track, b, at(500) ~~ at(1500), at(500))
    val current = timeline.getTrackOrCreate(0)
    val transition = current.get(at(600)) match {
      case t: Transition => t
      case other => fail("重叠区应是转场，实际: " + other)
    }
    assertEquals(timeline.timeline.segment(a.id), Some(a))
    assertEquals(timeline.timeline.segment(b.id), Some(b))
    assertEquals(timeline.timeline.segment(transition.id), Some(transition))
    assert(timeline.timeline.segment(Id.noId()).isEmpty)
  }

  test("移除后 id 不再解析，撤销后恢复") {
    val track = timeline.getTrackOrCreate(0)
    val a = new TestCont(1000)
    timeline.add(track, a, at(0) ~~ at(1000), Instant.Zero)
    timeline.snapshot()
    timeline.remove(a)
    assert(timeline.timeline.segment(a.id).isEmpty)
    project.undo.undo()
    assertEquals(timeline.timeline.segment(a.id), Some(a))
  }

  test("组只存 id") {
    val track = timeline.getTrackOrCreate(0)
    val a = new TestCont(1000)
    timeline.add(track, a, at(0) ~~ at(1000), Instant.Zero)
    val group = timeline.groupAdd(timeline.newGroup(), a)
    assertEquals(group.toSet, Set(a.id))
    assert(timeline.getGroup(a).exists(_.contains(a.id)))
  }

  test("maxId 取最大真实 id") {
    val track = timeline.getTrackOrCreate(0)
    val a = new TestCont(1000)
    val b = new TestCont(1000)
    timeline.add(track, a, at(0) ~~ at(1000), Instant.Zero)
    timeline.add(track, b, at(500) ~~ at(1500), at(500))
    assertEquals(timeline.timeline.maxId, Seq(a.id, b.id).max(Ordering[Id]))
  }
}
