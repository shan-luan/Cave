package com.lomekwi.cave.ui.tabs.edit

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.kotcrab.vis.ui.widget.tabbedpane.Tab
import com.lomekwi.cave.pipeline.NodeGraph
import com.lomekwi.cave.ui.node.NodeEditorView
import com.lomekwi.cave.util.i18n.I18N

//TODO:WIP
class NodeEditorTab(nodeGraph0: NodeGraph) extends Tab(false, true) {
  private final val nodeGraph: NodeGraph = nodeGraph0
  private final val content: Table = new NodeEditorView()

  def getNodeGraph(): NodeGraph = {
    nodeGraph
  }

  override def getTabTitle(): String = {
    I18N.i18n("节点编辑器")
  }

  override def getContentTable(): Table = {
    content
  }
}
