package com.lomekwi.cave.ui.editpanel.inspector

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.utils.Align
import com.kotcrab.vis.ui.VisUI
import com.kotcrab.vis.ui.widget.VisImageButton
import com.kotcrab.vis.ui.widget.VisTable
import com.lomekwi.cave.app.App
import com.lomekwi.cave.pipeline.Filter
import com.lomekwi.cave.pipeline.Source
import com.lomekwi.cave.project.Project
import com.lomekwi.cave.timeline.UndoManager
import com.lomekwi.cave.timeline.playback.RefreshRequestEvent
import com.lomekwi.cave.ui.widget.Card


import scala.compiletime.uninitialized
import scala.jdk.CollectionConverters.*
import java.util

/**
 * 过滤器节点卡：显示 filter 名称，可编辑其数值输入端口（默认值），支持删除、
 * 标题栏上下交换与拖拽重排。类型 → widget 的映射由 {@link CardWidgetsRegistry} 维护。
 */
final class FilterActor(private val source: Source[?], private val filter: Filter[?]) extends Card(filter.getName) {
  private var rebuildCallback: Runnable = uninitialized
  private var dragging: Boolean = false
  private var dragStageY: Float = 0
  private var dragWindowY: Float = 0

  align(Align.top | Align.left)
  defaults().left()
  addMoveButton(true)
  addMoveButton(false)
  addCloseButton()
  addListener(new InputListener {
    override def touchDown(event: InputEvent, x: Float, y: Float, pointer: Int, button: Int): Boolean = {
      // 按住标题栏区域即可拖动重排
      if (button == 0 && y >= getHeight - getPadTop) {
        dragging = true
        dragStageY = event.getStageY
        dragWindowY = getY
        event.cancel()
        true
      } else {
        false
      }
    }

    override def touchDragged(event: InputEvent, x: Float, y: Float, pointer: Int): Unit = {
      if (dragging) {
        event.cancel()
        setY(dragWindowY + (event.getStageY - dragStageY))
      }
    }

    override def touchUp(event: InputEvent, x: Float, y: Float, pointer: Int, button: Int): Unit = {
      if (dragging) {
        dragging = false
        doReorder()
        if (rebuildCallback != null) {
          rebuildCallback.run()
        }
      }
    }
  })
  for (in <- filter.getInPorts.asScala) {
    // 链端口由 filter 自身持有，其约束取决于链路而非参数类型，不作为卡片参数编辑
    if (in != filter.getFilterIn) {
      val widget = CardWidgetsRegistry.createEditor(in, source)
      // 未注册该端口类型的 widget，不显示
      if (widget != null) {
        add(widget).growX().pad(2).row()
      }
    }
  }

  /** 标题栏加上/下移动按钮，用于交换相邻 filter 的顺序。 */
  private def addMoveButton(up: Boolean): Unit = {
    val style = new VisImageButton.VisImageButtonStyle(
      VisUI.getSkin.get("close-window", classOf[VisImageButton.VisImageButtonStyle]))
    style.imageUp = VisUI.getSkin.getDrawable(if (up) "select-up" else "select-down")
    val button = new VisImageButton(style)
    getTitleTable.add(button)
    button.addListener(new ChangeListener {
      override def changed(event: ChangeListener.ChangeEvent, actor: Actor): Unit = {
        move(up)
      }
    })
    button.addListener(new ClickListener {
      override def touchDown(event: InputEvent, x: Float, y: Float, pointer: Int, button: Int): Boolean = {
        event.cancel()
        true
      }
    })
  }

  private def move(up: Boolean): Unit = {
    val filters = if (source == null) null else source.getFilters.asInstanceOf[util.List[Filter[?]]]
    if (filters != null) {
      val index = filters.indexOf(filter)
      val target = if (up) index - 1 else index + 1
      if (index >= 0 && target >= 0 && target < filters.size()) {
        filters.remove(index)
        filters.add(target, filter)
        val p: Project = App.root.getFrontendProject
        if (p != null) {
          p.undoManager.record(UndoManager.ReorderFilterCommand(source, filter, index, target))
          p.projEventBus.post(RefreshRequestEvent)
        }
        if (rebuildCallback != null) {
          rebuildCallback.run()
        }
      }
    }
  }

  /** 拖拽结束后，根据卡片在列表中的位置计算目标索引并重排。 */
  private def doReorder(): Unit = {
    if (source == null) return
    val p = getParent
    p match {
      case content: VisTable =>
        val filters = source.getFilters.asInstanceOf[util.List[Filter[?]]]
        val myIndex = filters.indexOf(filter)
        if (myIndex < 0) return

        val myCenterY = getY + getHeight / 2
        var target = 0
        for (child <- content.getChildren.asScala) {
          if (child.isInstanceOf[FilterActor] && child != this) {
            if (child.getY + child.getHeight / 2 > myCenterY) target += 1
          }
        }

        if (target == myIndex) return

        filters.remove(myIndex)
        filters.add(target, filter)

        val pj: Project = App.root.getFrontendProject
        if (pj != null) {
          pj.undoManager.record(UndoManager.ReorderFilterCommand(source, filter, myIndex, target))
          pj.projEventBus.post(RefreshRequestEvent)
        }
      case _ =>
    }
  }

  override def close(): Unit = {
    val index = source.getFilters.indexOf(filter)
    if (index >= 0) {
      source.getFilters.remove(filter)
      val p: Project = App.root.getFrontendProject
      if (p != null) {
        p.undoManager.record(UndoManager.RemoveFilterCommand(source, filter, index))
        p.projEventBus.post(RefreshRequestEvent)
      }
      remove()
      if (rebuildCallback != null) rebuildCallback.run()
    }
  }

  def setRebuildCallback(callback: Runnable): Unit = {
    this.rebuildCallback = callback
  }
}
