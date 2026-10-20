package com.lomekwi.cave.ui.tabs.edit

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.kotcrab.vis.ui.widget.tabbedpane.Tab
import com.lomekwi.cave.pipeline.NodeGraph
import com.lomekwi.cave.ui.node.NodeEditorView
import com.lomekwi.cave.util.i18n.*
import com.lomekwi.cave.util.i18n.BasicLang.*
import com.lomekwi.cave.util.i18n.TransTargetType.given

class NodeEditorTab(nodeGraph0: NodeGraph) extends Tab(false, true) {
  final val nodeGraph: NodeGraph = nodeGraph0
  private final val content: Table = new NodeEditorView(nodeGraph)

  override def getTabTitle: String = {
    ("节点编辑器".in(Zh) | "Node Editor".in(En)).str()
  }

  override def getContentTable: Table = {
    content
  }
}
