package com.lomekwi.cave.ui.editpanel.inspector

import com.lomekwi.cave.util.legacy_i18n.I18N.i18n

import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.google.common.eventbus.Subscribe
import com.lomekwi.cave.app.App
import com.lomekwi.cave.app.selection.{SegmentSet, SegmentSetSelectedEvent}
import com.kotcrab.vis.ui.widget.MenuItem
import com.kotcrab.vis.ui.widget.PopupMenu
import com.kotcrab.vis.ui.widget.VisLabel
import com.kotcrab.vis.ui.widget.VisScrollPane
import com.kotcrab.vis.ui.widget.VisTable
import com.kotcrab.vis.ui.widget.VisTextButton
import com.lomekwi.cave.pipeline.Filter
import com.lomekwi.cave.pipeline.Node
import com.lomekwi.cave.timeline.Segment
import com.lomekwi.cave.project.UndoManager


import scala.collection.mutable
import scala.compiletime.uninitialized


class Inspector extends VisTable {
  private final val content: VisTable = new VisTable()
  private var currentSegment: Segment = uninitialized
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
      showInfo(e.set.head)
    } else {
      showMultiInfo(e.set)
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
    val segments: mutable.ArrayBuffer[Segment] = mutable.ArrayBuffer.from(set)
    sortByPlacement(segments)
    var first = true
    for (segment <- segments) {
      if (!first) {
        content.row()
      }
      first = false
      appendSegmentInfo(segment)
    }
  }

  private def showInfo(segment: Segment): Unit = {
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
  private def sortByPlacement(segments: mutable.ArrayBuffer[Segment]): Unit = {
    val context = App.root.getFrontendContext
    if (context == null) return
    val timeline = context.timeline
    segments.sortInPlaceWith((a: Segment, b: Segment) => {
      val ta = timeline.findTrackOf(a)
      val tb = timeline.findTrackOf(b)
      val ia = ta.map(_.index).getOrElse(Integer.MAX_VALUE)
      val ib = tb.map(_.index).getOrElse(Integer.MAX_VALUE)
      val c = Integer.compare(ia, ib)
      if (c != 0) {
        c < 0
      } else {
        val ra = ta.map(_.getInterval(a).lo).getOrElse(Long.MaxValue)
        val rb = tb.map(_.getInterval(b).lo).getOrElse(Long.MaxValue)
        ra < rb
      }
    })
  }

  private def appendSegmentInfo(segment: Segment): Unit = {
    content.add(new SourceActor(segment)).growX().pad(4).row()
    for (filter <- segment.filters) {
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
      filterMenu.addItem(new MenuItem(created.name, (event: ChangeListener.ChangeEvent, actor: com.badlogic.gdx.scenes.scene2d.Actor) => {
        segment.filters += created.asInstanceOf[Filter[?]]
        val c = App.root.getFrontendContext
        if (c != null) c.touch()
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
