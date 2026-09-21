package com.lomekwi.cave.app.copy

import com.lomekwi.cave.app.selection.SourceSet
import com.lomekwi.cave.project.TestProject
import com.lomekwi.cave.timeline.{GdxTestBase, Interval, TestSource, Timeline}

import org.junit.Assert.{assertEquals, assertNotSame, assertNull, assertSame, assertTrue}
import org.junit.Before
import org.junit.Test

/**
 * 剪贴板模板。关键在于模板必须自带位置与组结构：
 * 选中集只描述"当前时间线里的哪些对象"，而模板要能脱离原对象、在时间轴任意位置重放。
 */
class PasteTemplateTest extends GdxTestBase {

  private var project: TestProject = null
  private var timeline: Timeline = null

  @Before
  def setUp(): Unit = {
    project = new TestProject()
    timeline = project.timeline
  }

  @Test
  def templateSnapshotsPlacementAndGroup(): Unit = {
    val t0 = timeline.getTrack(0)
    val t1 = timeline.getTrack(1)
    val a = new TestSource(100)
    val b = new TestSource(100)
    timeline.tryAdd(t0, a, Interval(0, 100), 1000)
    timeline.tryAdd(t1, b, Interval(500, 600), 2000)
    val group = timeline.newGroup()
    group.add(a)
    group.add(b)

    val selection = new SourceSet(timeline)
    selection.add(a)
    selection.add(b)
    val template = selection.copy().asInstanceOf[PasteTemplate]

    val entries = template.getEntries
    assertEquals(2, entries.size())

    val ea = entries.get(0)
    assertEquals(0, ea.trackIndex)
    assertEquals(Interval(0, 100), ea.range)
    assertEquals(1000, ea.origin)

    val eb = entries.get(1)
    assertEquals(1, eb.trackIndex)
    assertEquals(Interval(500, 600), eb.range)
    assertEquals(2000, eb.origin)

    // 组结构在模板里保留：成员共享同一个模板组
    assertTrue(ea.group != null)
    assertSame(ea.group, eb.group)
    assertEquals(2, ea.group.size())

    // 模板持有的是拷贝，且这些拷贝不在时间线上，位置只能从模板本身读
    assertNotSame(a, ea.source)
    assertNotSame(b, eb.source)
    assertNull(timeline.findTrackOf(ea.source))
    assertNull(timeline.findTrackOf(eb.source))
  }

  @Test
  def templateCopyRefreshesSourcesButKeepsPlacementAndGroupShape(): Unit = {
    val t0 = timeline.getTrack(0)
    val t1 = timeline.getTrack(1)
    val a = new TestSource(100)
    val b = new TestSource(100)
    timeline.tryAdd(t0, a, Interval(0, 100), 1000)
    timeline.tryAdd(t1, b, Interval(500, 600), 2000)
    val group = timeline.newGroup()
    group.add(a)
    group.add(b)

    val selection = new SourceSet(timeline)
    selection.add(a)
    selection.add(b)
    val first = selection.copy().asInstanceOf[PasteTemplate]
    val second = first.copy().asInstanceOf[PasteTemplate]

    // 两次粘贴必须拿到不同的源实例，否则第二次粘贴会复用已在时间线上的对象
    assertNotSame(first.getEntries.get(0).source, second.getEntries.get(0).source)

    assertEquals(first.getEntries.get(0).range, second.getEntries.get(0).range)
    assertEquals(first.getEntries.get(0).origin, second.getEntries.get(0).origin)
    assertEquals(first.getEntries.get(1).range, second.getEntries.get(1).range)
    assertEquals(first.getEntries.get(1).origin, second.getEntries.get(1).origin)

    // 组也是新建的，结构一致
    assertNotSame(first.getEntries.get(0).group, second.getEntries.get(0).group)
    assertSame(second.getEntries.get(0).group, second.getEntries.get(1).group)
    assertEquals(2, second.getEntries.get(0).group.size())
  }

  @Test
  def templateSurvivesDeletionOfOriginals(): Unit = {
    val t0 = timeline.getTrack(0)
    val a = new TestSource(100)
    timeline.tryAdd(t0, a, Interval(0, 100), 5000)

    val selection = new SourceSet(timeline)
    selection.add(a)
    val template = selection.copy().asInstanceOf[PasteTemplate]

    timeline.remove(a)

    assertEquals(1, template.getEntries.size())
    assertEquals(Interval(0, 100), template.getEntries.get(0).range)
    assertEquals(5000, template.getEntries.get(0).origin)
  }
}
