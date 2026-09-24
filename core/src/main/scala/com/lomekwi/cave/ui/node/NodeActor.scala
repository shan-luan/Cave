package com.lomekwi.cave.ui.node

import com.badlogic.gdx.Input
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.utils.DragListener
import com.kotcrab.vis.ui.widget.VisTable
import com.lomekwi.cave.app.App
import com.lomekwi.cave.pipeline.Node
import com.lomekwi.cave.pipeline.NodeGraph
import com.lomekwi.cave.ui.TextInputs
import com.lomekwi.cave.ui.widget.Card

import scala.collection.mutable
import scala.jdk.CollectionConverters.*

//TODO:WIP
class NodeActor(node0: Node) extends Card(node0.getName) {
  private final val node: Node = node0
  private final val inTable: VisTable = new VisTable()
  private final val outTable: VisTable = new VisTable()
  private final val portActors: mutable.HashMap[Node.Port, PortActor] = mutable.HashMap.empty[Node.Port, PortActor]

  inTable.top().left()
  inTable.defaults().left()
  outTable.top().right()
  outTable.defaults().right()
  add(inTable).top().left()
  add(outTable).top().right().growX()

  for (in <- node.getInPorts.asScala) {
    val editor: Actor = App.cardWidgetsRegistry.createEditor(in, null)
    val portActor = new InPortActor(in, editor.asInstanceOf[PortEditor & Actor])
    portActors.put(in, portActor)
    inTable.add(portActor).growX().row()
  }

  for (out <- node.getOutPorts.asScala) {
    val row: Actor = App.cardWidgetsRegistry.createOutputRow(out)
    if (row != null) {
      outTable.add(row).growX()
    }
    val portActor = new OutPortActor(out)
    portActors.put(out, portActor)
    outTable.add(portActor).row()
  }

  /** 本卡片中承载指定端口的端口圆点，没有则返回 null。 */
  def getPortActor(port: Node.Port): PortActor = {
    portActors.getOrElse(port, null)
  }

  def getNode: Node = {
    node
  }

  if (node.canRemove) {
    addCloseButton()
  }

  addListener(new DragListener {
    private var grabX: Float = 0f
    private var grabY: Float = 0f

    setButton(Input.Buttons.LEFT)

    override def touchDown(event: InputEvent, x: Float, y: Float, pointer: Int, button: Int): Boolean = {
      // 在端口编辑器的文本框上拖拽是在选字，不该把节点一起拖走
      if (nodeGraph.isEmpty || TextInputs.contains(event.getTarget) || !super.touchDown(event, x, y, pointer, button)) {
        false
      } else {
        grabX = x
        grabY = y
        true
      }
    }

    override def drag(event: InputEvent, x: Float, y: Float, pointer: Int): Unit = {
      moveBy(x - grabX, y - grabY)
      nodeGraph.foreach(graph => graph.setPosition(node, getX, getY))
    }
  })

  override protected def close(): Unit = {
    nodeGraph.foreach(graph => graph.remove(node))
    remove()
  }

  private def nodeGraph: Option[NodeGraph] = {
    Option(getParent)
      .flatMap(parent => Option(parent.getParent))
      .flatMap(parent => Option(parent.getParent))
      .collect { case view: NodeEditorView if view.getNodeGraph.contains(node) => view.getNodeGraph }
  }
}
