package com.lomekwi.cave.app.copy

import com.lomekwi.cave.app.selection.SegmentSet
import com.lomekwi.cave.project.TestProject
import com.lomekwi.cave.timeline.{GdxTestBase, Instant, Interval, TestCont, TestTimeline, Timespan, Timeline}
import com.lomekwi.cave.timeline.~~

/**
 * 剪贴板模板。
 */
class PasteTemplateTest extends GdxTestBase {

  private var project: TestProject = null
  private var timeline: TestTimeline = null

  private def at(value: Long): Instant = Instant.At(Timespan(value))

  private def interval(lo: Long, hi: Long): Interval = at(lo) ~~ at(hi)

  override def beforeEach(context: BeforeEach): Unit = {
    project = new TestProject()
    timeline = project.timeline
  }

  test("模板快照记录位置与组结构") {
    val t0 = timeline.getTrackOrCreate(0)
    val t1 = timeline.getTrackOrCreate(1)
    val a = new TestCont(100)
    val b = new TestCont(100)
    timeline.add(t0, a, interval(0, 100), at(1000))
    timeline.add(t1, b, interval(500, 600), at(2000))
    var group = timeline.newGroup()
    group = timeline.groupAdd(group, a)
    group = timeline.groupAdd(group, b)

    val selection = new SegmentSet(project.session)
    selection.add(a.id)
    selection.add(b.id)
    val template = selection.copy().asInstanceOf[PasteTemplate]

    val entries = template.entries
    assertEquals(entries.size, 2)

    val ea = entries(0)
    assertEquals(ea.trackIndex, 0)
    assertEquals(ea.interval, interval(0, 100))
    assertEquals(ea.origin, at(1000))

    val eb = entries(1)
    assertEquals(eb.trackIndex, 1)
    assertEquals(eb.interval, interval(500, 600))
    assertEquals(eb.origin, at(2000))

    assert(ea.group.isDefined)
    assertEquals(eb.group, ea.group)
    assertEquals(ea.group.map(_.size), Some(2))

    assert(!((a) eq (ea.segment)))
    assert(!((b) eq (eb.segment)))
    assert(timeline.findTrackOf(ea.segment).isEmpty)
    assert(timeline.findTrackOf(eb.segment).isEmpty)
  }

  test("模板副本刷新片段但保留位置与组形状") {
    val t0 = timeline.getTrackOrCreate(0)
    val t1 = timeline.getTrackOrCreate(1)
    val a = new TestCont(100)
    val b = new TestCont(100)
    timeline.add(t0, a, interval(0, 100), at(1000))
    timeline.add(t1, b, interval(500, 600), at(2000))
    var group = timeline.newGroup()
    group = timeline.groupAdd(group, a)
    group = timeline.groupAdd(group, b)

    val selection = new SegmentSet(project.session)
    selection.add(a.id)
    selection.add(b.id)
    val first = selection.copy().asInstanceOf[PasteTemplate]
    val second = first.copy().asInstanceOf[PasteTemplate]

    assert(!((first.entries(0).segment) eq (second.entries(0).segment)))

    assertEquals(second.entries(0).interval, first.entries(0).interval)
    assertEquals(second.entries(0).origin, first.entries(0).origin)
    assertEquals(second.entries(1).interval, first.entries(1).interval)
    assertEquals(second.entries(1).origin, first.entries(1).origin)

    assertNotEquals(first.entries(0).group, second.entries(0).group)
    assertEquals(second.entries(1).group, second.entries(0).group)
    assertEquals(second.entries(0).group.map(_.size), Some(2))
  }

  test("原片段删除后模板仍然可用") {
    val t0 = timeline.getTrackOrCreate(0)
    val a = new TestCont(100)
    timeline.add(t0, a, interval(0, 100), at(5000))

    val selection = new SegmentSet(project.session)
    selection.add(a.id)
    val template = selection.copy().asInstanceOf[PasteTemplate]

    timeline.remove(a)

    assertEquals(template.entries.size, 1)
    assertEquals(template.entries(0).interval, interval(0, 100))
    assertEquals(template.entries(0).origin, at(5000))
  }

  test("粘贴按相对位置落回并分配新 id") {
    val t0 = timeline.getTrackOrCreate(0)
    val a = new TestCont(100)
    timeline.add(t0, a, interval(0, 100), at(0))

    val selection = new SegmentSet(project.session)
    selection.add(a.id)
    val template = selection.copy().asInstanceOf[PasteTemplate]

    val pasted = template.paste(project.session, 0, at(1000))

    assertEquals(pasted.size, 1)
    val id = pasted.head
    assert(id != a.id)
    val after = project.session.timeline.getTrackOrCreate(0)
    assertEquals(after.interval(id), interval(1000, 1100))
    assertEquals(after.getOrigin(id), at(1000))
    assert(!(after.segment(id).source eq a.source))
  }
}
