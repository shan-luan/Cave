package com.lomekwi.cave.ui.node

import com.badlogic.gdx.Input
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.utils.DragListener
import com.lomekwi.cave.pipeline.Node
import com.lomekwi.cave.pipeline.NodeGraph
import com.lomekwi.cave.ui.widget.Card

//TODO:WIP
class NodeActor(node0: Node) extends Card(node0.getName) {
  private final val node: Node = node0

  def getNode: Node = {
    node
  }

  addListener(new DragListener {
    private var grabX: Float = 0f
    private var grabY: Float = 0f

    setButton(Input.Buttons.LEFT)

    override def touchDown(event: InputEvent, x: Float, y: Float, pointer: Int, button: Int): Boolean = {
      if (dragGraph.isEmpty || !super.touchDown(event, x, y, pointer, button)) {
        false
      } else {
        grabX = x
        grabY = y
        true
      }
    }

    override def drag(event: InputEvent, x: Float, y: Float, pointer: Int): Unit = {
      moveBy(x - grabX, y - grabY)
      dragGraph.foreach(graph => graph.setPosition(node, getX, getY))
    }
  })

  private def dragGraph: Option[NodeGraph] = {
    Option(getParent)
      .flatMap(parent => Option(parent.getParent))
      .flatMap(parent => Option(parent.getParent))
      .collect { case view: NodeEditorView if view.getNodeGraph.contains(node) => view.getNodeGraph }
  }
}
