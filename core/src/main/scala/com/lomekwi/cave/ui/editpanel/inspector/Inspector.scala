package com.lomekwi.cave.ui.editpanel.inspector

import com.lomekwi.cave.util.i18n.I18N.i18n

import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.google.common.eventbus.Subscribe
import com.lomekwi.cave.app.App
import com.lomekwi.cave.app.selection.{SegmentNodeChangedEvent, SegmentSet, SegmentSetSelectedEvent}
import com.kotcrab.vis.ui.widget.MenuItem
import com.kotcrab.vis.ui.widget.PopupMenu
import com.kotcrab.vis.ui.widget.VisLabel
import com.kotcrab.vis.ui.widget.VisScrollPane
import com.kotcrab.vis.ui.widget.VisTable
import com.kotcrab.vis.ui.widget.VisTextButton
import com.lomekwi.cave.pipeline.Filter
import com.lomekwi.cave.pipeline.Node
import com.lomekwi.cave.pipeline.Segment
import com.lomekwi.cave.timeline.UndoManager


import scala.compiletime.uninitialized
import scala.jdk.CollectionConverters.*
import java.util


class Inspector extends VisTable {
  private final val content: VisTable = new VisTable()
  private var currentSegment: Segment[?] = uninitialized
  private var currentSet: SegmentSet = uninitialized

  {
    val scrollPane = new VisScrollPane(content)
    add(scrollPane).grow()
    showEmpty()
  }

  @Subscribe
  def onSelectionChanged(e: SegmentSetSelectedEvent): Unit = {
    val count = e.selectedCount
    if (count == 0) {
      showEmpty()
    } else if (count == 1) {
      showInfo(e.set.iterator().next())
    } else {
      showMultiInfo(e.set)
    }
  }

  /** 节点图改动后重建，条件是这个片段正在被显示。 */
  @Subscribe
  def onSegmentNodeChanged(e: SegmentNodeChangedEvent): Unit = {
    val shown = if (currentSet != null) currentSet.contains(e.segment)
                else currentSegment != null && (currentSegment eq e.segment)
    if (shown) {
      rebuildContent()
    }
  }

  private def rebuildContent(): Unit = {
    if (currentSet != null) {
      showMultiInfo(currentSet)
    } else if (currentSegment != null) {
      showInfo(currentSegment)
    }
  }

  private def showEmpty(): Unit = {
    currentSegment = null
    currentSet = null
    content.clear()
    content.setFillParent(true)
    content.add(new VisLabel(i18n("未选择源"))).expand().center()
  }

  private def showMultiInfo(set: SegmentSet): Unit = {
    currentSegment = null
    currentSet = set
    content.clear()
    content.setFillParent(false)
    content.top()
    val segments: util.List[Segment[?]] = new util.ArrayList[Segment[?]](set)
    sortByPlacement(segments)
    var first = true
    for (segment <- segments.asScala) {
      if (!first) {
        content.row()
      }
      first = false
      appendSegmentInfo(segment)
    }
  }

  private def showInfo(segment: Segment[?]): Unit = {
    if (segment != null) {
      currentSegment = segment
      currentSet = null
      content.clear()
      content.setFillParent(false)
      content.top()
      appendSegmentInfo(segment)
    }
  }

  /** 按所在轨道、再按时间轴起点排序，让列表顺序与时间线一致。 */
  private def sortByPlacement(segments: util.List[Segment[?]]): Unit = {
    val project = App.root.getFrontendProject
    if (project == null) return
    val timeline = project.timeline
    segments.sort((a: Segment[?], b: Segment[?]) => {
      val ta = timeline.findTrackOf(a)
      val tb = timeline.findTrackOf(b)
      val ia = if (ta == null) Integer.MAX_VALUE else ta.index
      val ib = if (tb == null) Integer.MAX_VALUE else tb.index
      val c = Integer.compare(ia, ib)
      if (c != 0) {
        c
      } else {
        val ra = if (ta == null) null else ta.getRange(a)
        val rb = if (tb == null) null else tb.getRange(b)
        java.lang.Long.compare(if (ra == null) Long.MaxValue else ra.lo,
          if (rb == null) Long.MaxValue else rb.lo)
      }
    })
  }

  private def appendSegmentInfo(segment: Segment[?]): Unit = {
    content.add(new SourceActor(segment)).growX().pad(4).row()
    for (filter <- segment.getFilters.asScala) {
      val actor = new FilterActor(segment, filter)
      actor.setRebuildCallback(() => rebuildContent())
      content.add(actor).growX().pad(4).row()
    }
    val addBtn = new VisTextButton(i18n("+添加滤镜"))
    val filterMenu = new PopupMenu()
    val compatibleCount = App.nodeRegistry.getCompatibleCount(segment)
    for (fi <- 0 until compatibleCount) {
      val idx = fi
      val created: Node = App.nodeRegistry.createCompatible(segment, idx)
      filterMenu.addItem(new MenuItem(created.getName, (event: ChangeListener.ChangeEvent, actor: com.badlogic.gdx.scenes.scene2d.Actor) => {
        segment.getFilters.asInstanceOf[util.List[Filter[?]]].add(created.asInstanceOf[Filter[?]])
        val p = App.root.getFrontendProject
        if (p != null) p.undoManager.record(UndoManager.AddFilterCommand(p, segment, created.asInstanceOf[Filter[?]]))
        rebuildContent()
      }))
    }
    addBtn.addListener(new ChangeListener {
      override def changed(event: ChangeListener.ChangeEvent, actor: com.badlogic.gdx.scenes.scene2d.Actor): Unit = {
        filterMenu.showMenu(getStage, addBtn)
      }
    })
    content.add(addBtn).pad(4).left()
  }
}
