package com.lomekwi.cave.app.copy

import com.lomekwi.cave.app.selection.SegmentSet
import com.lomekwi.cave.project.TestProject
import com.lomekwi.cave.timeline.{GdxTestBase, TestCont, TestTimeline, Timeline}
import com.lomekwi.cave.timeline.~~

/**
 * 剪贴板模板。模板必须自带位置与组结构，才能脱离原对象、在时间轴任意位置重放。
 */
class PasteTemplateTest extends GdxTestBase {

  private var project: TestProject = null
  private var timeline: TestTimeline = null

  override def beforeEach(context: BeforeEach): Unit = {
    project = new TestProject()
    timeline = project.timeline
  }

  test("模板快照记录位置与组结构") {
    val t0 = timeline.getTrackOrCreate(0)
    val t1 = timeline.getTrackOrCreate(1)
    val a = new TestCont(100)
    val b = new TestCont(100)
    timeline.tryAdd(t0, a, 0 ~~ 100, 1000)
    timeline.tryAdd(t1, b, 500 ~~ 600, 2000)
    var group = timeline.newGroup()
    group = timeline.groupAdd(group, a)
    group = timeline.groupAdd(group, b)

    val selection = new SegmentSet(project.context)
    selection.add(a)
    selection.add(b)
    val template = selection.copy().asInstanceOf[PasteTemplate]

    val entries = template.entries
    assertEquals(entries.size, 2)

    val ea = entries(0)
    assertEquals(ea.trackIndex, 0)
    assertEquals(ea.interval, 0 ~~ 100)
    assertEquals(ea.origin, 1000L)

    val eb = entries(1)
    assertEquals(eb.trackIndex, 1)
    assertEquals(eb.interval, 500 ~~ 600)
    assertEquals(eb.origin, 2000L)

    // 组结构在模板里保留，成员共享同一个模板组
    assert(ea.group.isDefined)
    assertEquals(eb.group, ea.group)
    assertEquals(ea.group.map(_.size), Some(2))

    // 模板持有的是拷贝，且这些拷贝不在时间线上，位置只能从模板本身读
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
    timeline.tryAdd(t0, a, 0 ~~ 100, 1000)
    timeline.tryAdd(t1, b, 500 ~~ 600, 2000)
    var group = timeline.newGroup()
    group = timeline.groupAdd(group, a)
    group = timeline.groupAdd(group, b)

    val selection = new SegmentSet(project.context)
    selection.add(a)
    selection.add(b)
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
    timeline.tryAdd(t0, a, 0 ~~ 100, 5000)

    val selection = new SegmentSet(project.context)
    selection.add(a)
    val template = selection.copy().asInstanceOf[PasteTemplate]

    timeline.remove(a)

    assertEquals(template.entries.size, 1)
    assertEquals(template.entries(0).interval, 0 ~~ 100)
    assertEquals(template.entries(0).origin, 5000L)
  }
}
