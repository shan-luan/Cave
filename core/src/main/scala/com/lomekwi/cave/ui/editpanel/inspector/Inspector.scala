package com.lomekwi.cave.ui.editpanel.inspector

import com.lomekwi.cave.util.i18n.I18N.i18n

import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.google.common.eventbus.Subscribe
import com.lomekwi.cave.app.App
import com.lomekwi.cave.app.selection.{SourceNodeChangedEvent, SourceSet, SourceSetSelectedEvent}
import com.kotcrab.vis.ui.widget.MenuItem
import com.kotcrab.vis.ui.widget.PopupMenu
import com.kotcrab.vis.ui.widget.VisLabel
import com.kotcrab.vis.ui.widget.VisScrollPane
import com.kotcrab.vis.ui.widget.VisTable
import com.kotcrab.vis.ui.widget.VisTextButton
import com.lomekwi.cave.pipeline.Filter
import com.lomekwi.cave.pipeline.Node
import com.lomekwi.cave.pipeline.Source
import com.lomekwi.cave.timeline.UndoManager


import scala.compiletime.uninitialized
import scala.jdk.CollectionConverters.*
import java.util


class Inspector extends VisTable {
  private final val content: VisTable = new VisTable()
  private var currentSource: Source[?] = uninitialized
  private var currentSet: SourceSet = uninitialized

  {
    val scrollPane = new VisScrollPane(content)
    add(scrollPane).grow()
    showEmpty()
  }

  @Subscribe
  def onSelectionChanged(e: SourceSetSelectedEvent): Unit = {
    val count = e.selectedCount
    if (count == 0) {
      showEmpty()
    } else if (count == 1) {
      showInfo(e.set.iterator().next())
    } else {
      showMultiInfo(e.set)
    }
  }

  /** 节点图改动后重建，条件是这个源正在被显示。 */
  @Subscribe
  def onSourceNodeChanged(e: SourceNodeChangedEvent): Unit = {
    val shown = if (currentSet != null) currentSet.contains(e.source)
                else currentSource != null && (currentSource eq e.source)
    if (shown) {
      rebuildContent()
    }
  }

  private def rebuildContent(): Unit = {
    if (currentSet != null) {
      showMultiInfo(currentSet)
    } else if (currentSource != null) {
      showInfo(currentSource)
    }
  }

  private def showEmpty(): Unit = {
    currentSource = null
    currentSet = null
    content.clear()
    content.setFillParent(true)
    content.add(new VisLabel(i18n("未选择片段"))).expand().center()
  }

  private def showMultiInfo(set: SourceSet): Unit = {
    currentSource = null
    currentSet = set
    content.clear()
    content.setFillParent(false)
    content.top()
    val sources: util.List[Source[?]] = new util.ArrayList[Source[?]](set)
    sortByPlacement(sources)
    var first = true
    for (source <- sources.asScala) {
      if (!first) {
        content.row()
      }
      first = false
      appendSourceInfo(source)
    }
  }

  private def showInfo(source: Source[?]): Unit = {
    if (source != null) {
      currentSource = source
      currentSet = null
      content.clear()
      content.setFillParent(false)
      content.top()
      appendSourceInfo(source)
    }
  }

  /** 按所在轨道、再按时间轴起点排序，让列表顺序与时间线一致。 */
  private def sortByPlacement(sources: util.List[Source[?]]): Unit = {
    val project = App.root.getFrontendProject
    if (project == null) return
    val timeline = project.timeline
    sources.sort((a: Source[?], b: Source[?]) => {
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

  private def appendSourceInfo(source: Source[?]): Unit = {
    content.add(new SourceActor(source)).growX().pad(4).row()
    for (filter <- source.getFilters.asScala) {
      val actor = new FilterActor(source, filter)
      actor.setRebuildCallback(() => rebuildContent())
      content.add(actor).growX().pad(4).row()
    }
    val addBtn = new VisTextButton(i18n("+添加滤镜"))
    val filterMenu = new PopupMenu()
    val compatibleCount = App.nodeRegistry.getCompatibleCount(source)
    for (fi <- 0 until compatibleCount) {
      val idx = fi
      val created: Node = App.nodeRegistry.createCompatible(source, idx)
      filterMenu.addItem(new MenuItem(created.getName, (event: ChangeListener.ChangeEvent, actor: com.badlogic.gdx.scenes.scene2d.Actor) => {
        source.getFilters.asInstanceOf[util.List[Filter[?]]].add(created.asInstanceOf[Filter[?]])
        val p = App.root.getFrontendProject
        if (p != null) p.undoManager.record(UndoManager.AddFilterCommand(p, source, created.asInstanceOf[Filter[?]]))
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
