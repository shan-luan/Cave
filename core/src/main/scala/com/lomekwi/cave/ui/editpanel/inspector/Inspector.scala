package com.lomekwi.cave.ui.editpanel.inspector

import com.lomekwi.cave.util.i18n.I18N.i18n

import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.google.common.eventbus.Subscribe
import com.lomekwi.cave.app.App
import com.kotcrab.vis.ui.widget.MenuItem
import com.kotcrab.vis.ui.widget.PopupMenu
import com.kotcrab.vis.ui.widget.VisLabel
import com.kotcrab.vis.ui.widget.VisScrollPane
import com.kotcrab.vis.ui.widget.VisTable
import com.kotcrab.vis.ui.widget.VisTextButton
import com.lomekwi.cave.pipeline.Filter
import com.lomekwi.cave.pipeline.Node
import com.lomekwi.cave.pipeline.Source
import com.lomekwi.cave.timeline.Segment
import com.lomekwi.cave.timeline.SegmentSet
import com.lomekwi.cave.timeline.SegmentSetSelectedEvent
import com.lomekwi.cave.timeline.UndoManager
import com.lomekwi.cave.timeline.SegmentSelectedEvent

import java.util.ArrayList
import java.util.List

import scala.jdk.CollectionConverters.*


class Inspector extends VisTable {
  private final val content: VisTable = new VisTable()
  private var currentSeg: Segment = null
  private var currentSegSet: SegmentSet = null

  {
    val scrollPane = new VisScrollPane(content)
    add(scrollPane).grow()
    showEmpty()
  }

  @Subscribe
  def onSegmentSelected(e: SegmentSelectedEvent): Unit = {
    val count = e.selectedCount
    if (count == 0) {
      showEmpty()
    } else if (count == 1 && e.segment != null) {
      showInfo(e.segment)
    }
  }

  @Subscribe
  def onSegmentSetSelected(e: SegmentSetSelectedEvent): Unit = {
    if (e.selectedCount > 1) {
      showMultiInfo(e.set)
    }
  }

  def rebuildContent(): Unit = {
    if (currentSegSet != null) {
      showMultiInfo(currentSegSet)
    } else if (currentSeg != null) {
      showInfo(currentSeg)
    }
  }

  private def showEmpty(): Unit = {
    currentSeg = null
    currentSegSet = null
    content.clear()
    content.setFillParent(true)
    content.add(new VisLabel(i18n("未选择片段"))).expand().center()
  }

  private def showMultiInfo(set: SegmentSet): Unit = {
    currentSeg = null
    currentSegSet = set
    content.clear()
    content.setFillParent(false)
    content.top()
    val segs: List[Segment] = new ArrayList[Segment](set)
    segs.sort(null)
    var first = true
    for (seg <- segs.asScala) {
      if (!first) {
        content.row()
      }
      first = false
      appendSegmentInfo(seg)
    }
  }

  private def showInfo(seg: Segment): Unit = {
    if (seg == null) return
    currentSeg = seg
    currentSegSet = null
    content.clear()
    content.setFillParent(false)
    content.top()
    appendSegmentInfo(seg)
  }

  private def appendSegmentInfo(seg: Segment): Unit = {
    val source = seg.getSource()
    content.add(new SourceActor(source)).growX().pad(4).row()
    for (filter <- source.getFilters().asScala) {
      val actor = new FilterActor(source, filter)
      actor.setRebuildCallback(() => rebuildContent())
      content.add(actor).growX().pad(4).row()
    }
    val addBtn = new VisTextButton(i18n("+添加滤镜"))
    val filterMenu = new PopupMenu()
    val compatibleCount = App.nodeRegistry.getCompatibleCount(source)
    var fi = 0
    while (fi < compatibleCount) {
      val idx = fi
      val created: Node = App.nodeRegistry.createCompatible(source, idx)
      filterMenu.addItem(new MenuItem(created.getName(), new ChangeListener {
        override def changed(event: ChangeListener.ChangeEvent, actor: com.badlogic.gdx.scenes.scene2d.Actor): Unit = {
          source.getFilters().asInstanceOf[List[Filter[?]]].add(created.asInstanceOf[Filter[?]])
          val p = App.root.getFrontendProject()
          if (p != null) p.undoManager.record(new UndoManager.AddFilterCommand(source, created.asInstanceOf[Filter[?]]))
          rebuildContent()
        }
      }))
      fi += 1
    }
    addBtn.addListener(new ChangeListener {
      override def changed(event: ChangeListener.ChangeEvent, actor: com.badlogic.gdx.scenes.scene2d.Actor): Unit = {
        filterMenu.showMenu(getStage(), addBtn)
      }
    })
    content.add(addBtn).pad(4).left()
  }
}
