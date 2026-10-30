package com.lomekwi.cave.timeline

import com.lomekwi.cave.project.{Id, TestProject}

import scala.util.Using

class TrackIdTest extends GdxTestBase {

  private var project: TestProject = null
  private var timeline: TestTimeline = null

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
    timeline.add(track, a, 0L ~~ 1000L, 0L)
    timeline.add(track, b, 500L ~~ 1500L, 500L)
    val current = timeline.getTrackOrCreate(0)
    val transition = current.get(600L) match {
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
    timeline.add(track, a, 0L ~~ 1000L, 0L)
    Using.resource(timeline.record()) { _ => timeline.remove(a) }
    assert(timeline.timeline.segment(a.id).isEmpty)
    project.undo.undo()
    assertEquals(timeline.timeline.segment(a.id), Some(a))
  }

  test("组只存 id") {
    val track = timeline.getTrackOrCreate(0)
    val a = new TestCont(1000)
    timeline.add(track, a, 0L ~~ 1000L, 0L)
    val group = timeline.groupAdd(timeline.newGroup(), a)
    assertEquals(group.toSet, Set(a.id))
    assert(timeline.getGroup(a).exists(_.contains(a.id)))
  }

  test("maxId 取最大真实 id") {
    val track = timeline.getTrackOrCreate(0)
    val a = new TestCont(1000)
    val b = new TestCont(1000)
    timeline.add(track, a, 0L ~~ 1000L, 0L)
    timeline.add(track, b, 500L ~~ 1500L, 500L)
    assertEquals(timeline.timeline.maxId, Seq(a.id, b.id).max(Ordering[Id]))
  }
}
