package com.lomekwi.cave.ui.node

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.math.Vector2
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

class NodeActor(node0: Node) extends Card(node0.name) {
  final val node: Node = node0
  private final val inTable: VisTable = new VisTable()
  private final val outTable: VisTable = new VisTable()
  private final val portActors: mutable.HashMap[Node.Port, PortActor] = mutable.HashMap.empty[Node.Port, PortActor]

  inTable.top().left()
  inTable.defaults().left()
  outTable.top().right()
  outTable.defaults().right()
  add(inTable).top().left()
  add(outTable).top().right().growX()

  for (in <- node.inPorts.asScala) {
    val editor: Actor = App.cardWidgetsRegistry.createEditor(in, null)
    val portActor = new InPortActor(in, editor.asInstanceOf[PortEditor & Actor])
    portActors.put(in, portActor)
    inTable.add(portActor).growX().row()
  }

  for (out <- node.outPorts.asScala) {
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

  if (node.canRemove) {
    addCloseButton()
  }

  private final val dragPos: Vector2 = new Vector2()
  // 按下时鼠标在卡片本地坐标系中的位置，拖拽目标是鼠标回到这个偏移
  private var grabX: Float = 0f
  private var grabY: Float = 0f

  private final val dragListener: DragListener = new DragListener {
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
      applyDrag(x, y)
    }
  }
  addListener(dragListener)

  /** 把卡片贴到鼠标下；x/y 是鼠标在卡片本地坐标系中的位置。 */
  private def applyDrag(x: Float, y: Float): Unit = {
    moveBy(x - grabX, y - grabY)
    nodeGraph.foreach(graph => graph.setPosition(node, getX, getY))
  }

  /** 拖拽中每帧重算鼠标位置，画布在拖拽期间平移缩放后卡片仍跟手。 */
  override def act(delta: Float): Unit = {
    super.act(delta)
    if (dragListener.isDragging && getStage != null) {
      dragPos.set(Gdx.input.getX.toFloat, Gdx.input.getY.toFloat)
      getStage.screenToStageCoordinates(dragPos)
      stageToLocalCoordinates(dragPos)
      applyDrag(dragPos.x, dragPos.y)
    }
  }

  override protected def close(): Unit = {
    nodeGraph.foreach(graph => graph.remove(node))
    remove()
  }

  private def nodeGraph: Option[NodeGraph] = {
    Option(getParent)
      .flatMap(parent => Option(parent.getParent))
      .flatMap(parent => Option(parent.getParent))
      .collect { case view: NodeEditorView if view.nodeGraph.contains(node) => view.nodeGraph }
  }
}
