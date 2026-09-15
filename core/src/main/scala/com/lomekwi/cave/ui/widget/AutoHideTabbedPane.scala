package com.lomekwi.cave.ui.widget

import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.actions.TemporalAction
import com.badlogic.gdx.scenes.scene2d.ui.Cell
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.Value
import com.kotcrab.vis.ui.widget.tabbedpane.Tab
import com.kotcrab.vis.ui.widget.tabbedpane.TabbedPane
import com.kotcrab.vis.ui.widget.tabbedpane.TabbedPaneAdapter

class AutoHideTabbedPane extends TabbedPane {
  private var visible: Boolean = true

  {
    addListener(new TabbedPaneAdapter {
      override def removedTab(tab: Tab): Unit = {
        updateVisibility()
      }

      override def removedAllTabs(): Unit = {
        updateVisibility()
      }
    })
  }

  override def add(tab: Tab): Unit = {
    super.add(tab)
    updateVisibility()
  }

  override def insert(index: Int, tab: Tab): Unit = {
    super.insert(index, tab)
    updateVisibility()
  }

  /** 必须在面板的 table 加入其布局之后调用。 */
  def refreshVisibility(): Unit = {
    updateVisibility()
  }

  private def updateVisibility(): Unit = {
    val table: Table = getTable
    val parent: Table = table.getParent match {
      case t: Table => t
      case _ => null
    }
    if (parent != null) {
      val cell: Cell[?] = parent.getCell(table)
      if (cell != null) {
        val show: Boolean = getTabs.size > 1
        if (show != visible) {
          visible = show

          table.clearActions()
          if (show) {
            table.setVisible(true)
            animateCellHeight(table, cell, cell.getPrefHeight, table.getPrefHeight)
          } else {
            animateCellHeight(table, cell, table.getHeight, 0)
          }
        }
      }
    }
  }

  private def animateCellHeight(table: Table, cell: Cell[?], startHeight: Float, endHeight: Float): Unit = {
    val show: Boolean = endHeight != 0
    val startAlpha: Float = if (show) 0 else table.getColor.a
    if (show) table.getColor.a = 0

    table.addAction(new TemporalAction(AutoHideTabbedPane.ANIMATION_DURATION, Interpolation.smooth) {
      override protected def update(percent: Float): Unit = {
        cell.height(startHeight + (endHeight - startHeight) * percent)
        table.getColor.a = startAlpha + ((if (show) 1 else 0) - startAlpha) * percent
        table.invalidateHierarchy()
      }

      override protected def end(): Unit = {
        table.getColor.a = 1
        if (show) {
          cell.height(Value.prefHeight)
        } else {
          cell.height(0)
          table.setVisible(false)
        }
        table.invalidateHierarchy()
      }
    })
  }
}

object AutoHideTabbedPane {
  private final val ANIMATION_DURATION: Float = 0.25f
}
