package com.lomekwi.cave.ui.node

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.utils.Align
import com.kotcrab.vis.ui.widget.VisTable
import com.kotcrab.vis.ui.widget.VisTextButton
import com.lomekwi.cave.app.App
import com.lomekwi.cave.pipeline.Node
import com.lomekwi.cave.pipeline.NodeGraph
import com.lomekwi.cave.pipeline.Source
import com.lomekwi.cave.ui.editpanel.EditPanel

//TODO:WIP
/**
 * NodeGraph 输入端口编辑 widget：按钮，按下后在项目的内部标签栏打开绑定到该端口当前默认值节点图的编辑器。
 * 外层表格占满卡片宽度，按钮保持自身尺寸左对齐，避免被卡片的 growX 拉伸。
 */
final class NodeGraphPortEditor(port0: Node.InPort[?], source: Source[?]) extends VisTable with PortEditor {
  private final val port: Node.InPort[?] = port0
  {
    val button: VisTextButton = new VisTextButton(port.getName)
    button.addListener(new ChangeListener {
      override def changed(event: ChangeListener.ChangeEvent, actor: Actor): Unit = {
        openEditor()
      }
    })
    align(Align.left)
    defaults().left()
    add(button)
  }

  private def openEditor(): Unit = {
    val nodeGraph: NodeGraph = port.getDefaultData.asInstanceOf[NodeGraph]
    val panel: EditPanel = App.root.getFrontendEditPanel
    if (nodeGraph != null && panel != null) {
      panel.getTlTabs.openNodeEditor(nodeGraph)
    }
  }

  override def getPort: Node.InPort[?] = port
}
