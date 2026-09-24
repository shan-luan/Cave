package com.lomekwi.cave.ui.node

import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.kotcrab.vis.ui.widget.MenuItem
import com.kotcrab.vis.ui.widget.PopupMenu
import com.kotcrab.vis.ui.widget.VisTable
import com.lomekwi.cave.app.App
import com.lomekwi.cave.pipeline.Node
import com.lomekwi.cave.pipeline.NodeGraph
import com.lomekwi.cave.ui.Colors
import com.lomekwi.cave.ui.Focusable
import com.lomekwi.cave.ui.listeners.ChangeListenerX
import com.lomekwi.cave.ui.widget.PanZoomCanvas
import space.earlygrey.shapedrawer.ShapeDrawer

import scala.collection.mutable
import scala.jdk.CollectionConverters.*

class NodeEditorView(nodeGraph0: NodeGraph) extends VisTable with Focusable {
  private final val nodeGraph: NodeGraph = nodeGraph0
  private val panZoom: PanZoomCanvas = new PanZoomCanvas(0.1f, 4f, 1000f)
  private val canvas: Group = panZoom.getCanvas
  private final val displayedNodes: mutable.HashSet[Node] = mutable.HashSet.empty[Node]
  private final val nodeMenu: PopupMenu = new PopupMenu()
  private final val spawnPos: Vector2 = new Vector2()
  private var dirty: Boolean = false

  setFillParent(true)
  add(panZoom).grow()
  setupListener()
  setupContextMenu()
  buildNodeMenu()
  syncNodeActors()
  placeNodeActors()

  override def act(delta: Float): Unit = {
    super.act(delta)
    if (dirty || !isUpToDate) {
      syncNodeActors()
      placeNodeActors()
      dirty = false
    }
  }

  def markDirty(): Unit = {
    dirty = true
  }

  def getNodeGraph: NodeGraph = {
    nodeGraph
  }

  private def isUpToDate: Boolean = {
    displayedNodes.size == nodeGraph.size() && displayedNodes.forall(node => nodeGraph.contains(node))
  }

  private def syncNodeActors(): Unit = {
    val toAdd: mutable.HashSet[Node] = mutable.HashSet.from(nodeGraph.asScala)
    toAdd --= displayedNodes
    val toRemove: mutable.HashSet[Node] = mutable.HashSet.from(displayedNodes)
    toRemove --= nodeGraph.asScala

    for (actor <- canvas.getChildren.asScala.toSeq) {
      actor match {
        case nodeActor: NodeActor if toRemove.contains(nodeActor.getNode) =>
          nodeActor.remove()
        case _ =>
      }
    }

    for (node <- toAdd) {
      val actor = new NodeActor(node)
      actor.pack()
      canvas.addActor(actor)
    }

    displayedNodes --= toRemove
    displayedNodes ++= toAdd
  }

  private def placeNodeActors(): Unit = {
    for (actor <- canvas.getChildren.asScala) {
      actor match {
        case nodeActor: NodeActor =>
          val position = nodeGraph.getPosition(nodeActor.getNode)
          nodeActor.setPosition(position.x, position.y)
        case _ =>
      }
    }
  }

  private def setupListener(): Unit = {
    addListener(new ClickListener {
      override def touchDown(event: InputEvent, x: Float, y: Float, pointer: Int, button: Int): Boolean = {
        false
      }

      override def scrolled(event: InputEvent, x: Float, y: Float, amountX: Float, amountY: Float): Boolean = {
        panZoom.zoomAt(event.getStageX, event.getStageY, amountY)
        true
      }
    })
  }

  /** 在空画布上右键弹出节点创建菜单，创建位置取右键处。 */
  private def setupContextMenu(): Unit = {
    panZoom.addListener(new InputListener {
      override def touchDown(event: InputEvent, x: Float, y: Float, pointer: Int, button: Int): Boolean = {
        if (button == Input.Buttons.RIGHT && event.getTarget.eq(panZoom)) {
          spawnPos.set(event.getStageX, event.getStageY)
          canvas.stageToLocalCoordinates(spawnPos)
          true
        } else {
          false
        }
      }

      override def touchUp(event: InputEvent, x: Float, y: Float, pointer: Int, button: Int): Unit = {
        if (button == Input.Buttons.RIGHT && event.getTarget.eq(panZoom)) {
          nodeMenu.showMenu(getStage, event.getStageX, event.getStageY)
        }
      }
    })
  }

  private def buildNodeMenu(): Unit = {
    for (i <- 0 until App.nodeRegistry.getCount) {
      val name: String = App.nodeRegistry.create(i).getName
      nodeMenu.addItem(new MenuItem(name, new ChangeListenerX(() => addNode(App.nodeRegistry.create(i)))))
    }
  }

  private def addNode(node: Node): Unit = {
    if (nodeGraph.add(node)) {
      nodeGraph.setPosition(node, spawnPos.x, spawnPos.y)
      markDirty()
    }
  }

  override def draw(batch: Batch, parentAlpha: Float): Unit = {
    val drawer = App.root.getShapeDrawer
    drawer.filledRectangle(getX, getY, getWidth, getHeight, Colors.NODE_BG)
    drawGrid(drawer)
    super.draw(batch, parentAlpha)
  }

  private def drawGrid(drawer: ShapeDrawer): Unit = {
    val ox = getX + canvas.getX
    val oy = getY + canvas.getY
    val s = canvas.getScaleX
    val spacing = NodeEditorView.GRID_SPACING * s
    if (spacing >= NodeEditorView.MIN_GRID_PIXEL) {
      val x0 = getX
      val x1 = getX + getWidth
      val y0 = getY
      val y1 = getY + getHeight

      val startX = Math.ceil(((x0 - ox) / spacing).toDouble).toFloat * spacing
      var x = ox + startX
      while (x <= x1) {
        drawer.line(x, y0, x, y1, Colors.NODE_GRID, 1f)
        x += spacing
      }

      val startY = Math.ceil(((y0 - oy) / spacing).toDouble).toFloat * spacing
      var y = oy + startY
      while (y <= y1) {
        drawer.line(x0, y, x1, y, Colors.NODE_GRID, 1f)
        y += spacing
      }
    }
  }
}

object NodeEditorView {
  private final val GRID_SPACING: Float = 50f
  private final val MIN_GRID_PIXEL: Float = 8f
}
