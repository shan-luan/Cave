package com.lomekwi.cave.ui.tabs.edit

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.kotcrab.vis.ui.widget.tabbedpane.Tab
import com.lomekwi.cave.pipeline.NodeGraphFilter
import com.lomekwi.cave.ui.node.NodeEditorView
import com.lomekwi.cave.util.i18n.*
import com.lomekwi.cave.util.i18n.BasicLang.*

class NodeEditorTab(nodeGraphFilter0: NodeGraphFilter, path0: Vector[NodeGraphFilter.Owner]) extends Tab(false, true) {
  final val nodeGraphFilter: NodeGraphFilter = nodeGraphFilter0
  final val path: Vector[NodeGraphFilter.Owner] = path0
  private final val content: Table = new NodeEditorView(nodeGraphFilter, path)

  override def getTabTitle: String = {
    ("节点编辑器".in(Zh) | "Node Editor".in(En)).str()
  }

  override def getContentTable: Table = {
    content
  }
}
