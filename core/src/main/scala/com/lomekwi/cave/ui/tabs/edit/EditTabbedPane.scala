package com.lomekwi.cave.ui.tabs.edit

import com.badlogic.gdx.scenes.scene2d.ui.Container
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.kotcrab.vis.ui.widget.tabbedpane.Tab
import com.kotcrab.vis.ui.widget.tabbedpane.TabbedPaneListener
import com.lomekwi.cave.pipeline.NodeGraph
import com.lomekwi.cave.ui.widget.AutoHideTabbedPane

class EditTabbedPane extends AutoHideTabbedPane {
  private final val contentHost: Container[Table] = new Container[Table] {
    override def getMinHeight: Float = {
      0
    }
    override def getMinWidth: Float = {
      0
    }
    override def getPrefHeight: Float = {
      0
    }
    override def getPrefWidth: Float = {
      0
    }
  }

  {
    contentHost.fill()
    addListener(new TabbedPaneListener {
      override def switchedTab(tab: Tab): Unit = {
        contentHost.setActor(if (tab == null) null else tab.getContentTable)
      }

      override def removedTab(tab: Tab): Unit = {
        if (getTabs.size == 0) contentHost.setActor(null)
      }

      override def removedAllTabs(): Unit = {
        contentHost.setActor(null)
      }
    })
  }

  def getContentHost: Container[Table] = {
    contentHost
  }

  //TODO:WIP
  /** 打开绑定到指定节点图的编辑器标签页；已为同一个节点图打开过则直接切换过去。 */
  def openNodeEditor(nodeGraph: NodeGraph): Unit = {
    val tabs = getTabs
    val existing = (0 until tabs.size).map(i => tabs.get(i)).collectFirst {
      case editor: NodeEditorTab if editor.getNodeGraph eq nodeGraph => editor
    }
    existing match {
      case Some(editor) =>
        switchTab(editor)
      case None =>
        val editor = new NodeEditorTab(nodeGraph)
        add(editor)
        switchTab(editor)
    }
  }
}
