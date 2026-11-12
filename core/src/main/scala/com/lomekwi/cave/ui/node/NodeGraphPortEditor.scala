package com.lomekwi.cave.ui.node

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.utils.Align
import com.kotcrab.vis.ui.widget.VisLabel
import com.kotcrab.vis.ui.widget.VisTable
import com.kotcrab.vis.ui.widget.VisTextButton
import com.lomekwi.cave.app.App
import com.lomekwi.cave.pipeline.{Filter, Node, NodeGraphFilter}
import com.lomekwi.cave.project.Id
import com.lomekwi.cave.timeline.Segment
import com.lomekwi.cave.ui.editpanel.EditPanel
import com.lomekwi.cave.util.i18n.*
import com.lomekwi.cave.util.i18n.BasicLang.*

/**
 * 节点图输入端口编辑 widget，端口名标签加一个按钮，按下后在项目的内部标签栏打开所属节点图的编辑器。
 * 外层表格占满卡片宽度，标签与按钮保持自身尺寸左对齐，避免被卡片的 growX 拉伸。
 */
final class NodeGraphPortEditor(port0: Node.InPort[?], owner: Node, segment: Segment) extends VisTable with PortEditor {
  override val port: Node.InPort[?] = port0
  override val label: VisLabel = new VisLabel(port.name)
  private val button: VisTextButton = new VisTextButton(("编辑".in(Zh) | "Edit".in(En)).str())
  private val filterId: Id = if (segment == null) Id.noId() else segment.chain.filterId(owner.asInstanceOf[Filter[?]])

  button.addListener(new ChangeListener {
    override def changed(event: ChangeListener.ChangeEvent, actor: Actor): Unit = {
      openEditor()
    }
  })
  align(Align.left)
  defaults().left()
  add(label).pad(2f)
  add(button).pad(2f)

  private def openEditor(): Unit = {
    val panel: EditPanel = App.root.getFrontendEditPanel
    if (panel == null) {
      return
    }
    if (segment != null) {
      val c = App.root.getFrontendSession
      val currentSegment = if (c == null) segment else c.timeline.segment(segment.id).orNull
      if (currentSegment != null) {
        currentSegment.chain.filterById(filterId) match {
          case nodeGraphFilter: NodeGraphFilter =>
            panel.tlTabs.openNodeEditor(nodeGraphFilter, Vector(NodeGraphFilter.InChain(currentSegment.id, filterId)))
          case _ =>
        }
      }
    } else {
      val view = enclosingView
      if (view != null) {
        val nodeId = view.currentGraph.idOf(owner)
        if (nodeId != Id.noId()) {
          view.currentGraph.nodes.get(nodeId) match {
            case Some(nodeGraphFilter: NodeGraphFilter) =>
              panel.tlTabs.openNodeEditor(nodeGraphFilter, view.path :+ NodeGraphFilter.InGraph(nodeId))
            case _ =>
          }
        }
      }
    }
  }

  private def enclosingView: NodeEditorView = {
    var current: Actor = getParent
    while (current != null) {
      current match {
        case view: NodeEditorView => return view
        case _ => current = current.getParent
      }
    }
    null
  }

  override def control: Actor = button
}
