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
import com.lomekwi.cave.pipeline.{GraphInNode, Node, NodeGraph, NodeGraphFilter, Sink}
import com.lomekwi.cave.project.IdSource
import com.lomekwi.cave.ui.Colors
import com.lomekwi.cave.ui.Focusable
import com.lomekwi.cave.ui.listeners.ChangeListenerX
import com.lomekwi.cave.ui.widget.PanZoomCanvas
import space.earlygrey.shapedrawer.ShapeDrawer

import scala.collection.mutable
import scala.jdk.CollectionConverters.*

class NodeEditorView(initial: NodeGraphFilter, val path: Vector[NodeGraphFilter.Owner]) extends VisTable with Focusable {
  private var fallbackNgf: NodeGraphFilter = initial
  private val panZoom: PanZoomCanvas = new PanZoomCanvas(0.1f, 4f, 1000f)
  private val canvas: Group = panZoom.canvas
  private final val displayedNodes: mutable.HashSet[Node] = mutable.HashSet.empty[Node]
  private final val spawnPositions: mutable.HashMap[Node, Vector2] = mutable.HashMap.empty[Node, Vector2]
  private var cascade: Int = 0
  private final val nodeMenu: PopupMenu = new PopupMenu()
  private final val spawnPos: Vector2 = new Vector2()
  private var dirty: Boolean = false

  setFillParent(true)
  add(panZoom).grow()
  setupListener()
  setupContextMenu()
  buildNodeMenu()
  syncNodeActors()

  def link(in: Node.InPort[?], out: Node.OutPort[?]): Unit = {
    update(graph.link(in, out))
  }

  def unlink(in: Node.InPort[?]): Unit = {
    update(graph.unlink(in))
  }

  def prev(in: Node.InPort[?]): Node.OutPort[?] = {
    graph.prev(in)
  }

  def next(out: Node.OutPort[?]): Vector[Node.InPort[?]] = {
    graph.next(out)
  }

  def removeNode(node: Node): Unit = {
    update(graph.remove(node))
  }

  def defaultOf(port: Node.InPort[?]): Any = graph.defaultOf(port)

  def setDefault(port: Node.InPort[?], value: Any): Unit = {
    update(graph.setDefault(port, value))
  }

  def currentGraph: NodeGraph = currentNgf.innerNodes

  private def graph: NodeGraph = currentNgf.innerNodes

  //FIXME：端口绘制/act 每次访问都走一遍 graphFor（扫 timeline + 路径下探），应按 version 缓存。
  private def currentNgf: NodeGraphFilter = {
    val c = App.root.getFrontendSession
    val resolved = if (c == null) null else c.graphFor(path)
    if (resolved == null) fallbackNgf else resolved
  }

  private def update(newGraph: NodeGraph): Unit = {
    val next = currentNgf.withInnerNodes(newGraph)
    val c = App.root.getFrontendSession
    if (c != null) {
      c.commitGraph(path, next)
    } else {
      fallbackNgf = next
    }
  }

  override def act(delta: Float): Unit = {
    super.act(delta)
    if (dirty || !isUpToDate) {
      syncNodeActors()
      dirty = false
    }
  }

  private def markDirty(): Unit = {
    dirty = true
  }

  private def isUpToDate: Boolean = {
    displayedNodes.size == graph.size && displayedNodes.forall(node => graph.contains(node))
  }

  private def syncNodeActors(): Unit = {
    val toAdd: mutable.HashSet[Node] = mutable.HashSet.from(graph)
    toAdd --= displayedNodes
    val toRemove: mutable.HashSet[Node] = mutable.HashSet.from(displayedNodes)
    toRemove --= graph

    for (actor <- canvas.getChildren.asScala.toSeq) {
      actor match {
        case nodeActor: NodeActor if toRemove.contains(nodeActor.node) =>
          nodeActor.remove()
        case _ =>
      }
    }

    for (node <- toAdd) {
      val actor = new NodeActor(node)
      actor.pack()
      val spawn: Vector2 = spawnPositions.remove(node).orNull
      if (spawn != null) {
        actor.setPosition(spawn.x, spawn.y)
      } else {
        node match {
          case _: GraphInNode =>
            actor.setPosition(0f, 0f)
          case _: Sink =>
            actor.setPosition(NodeEditorView.SINK_OFFSET_X, 0f)
          case _ =>
            actor.setPosition(
              NodeEditorView.CASCADE_START_X + (cascade % NodeEditorView.CASCADE_COLUMNS) * NodeEditorView.CASCADE_STEP_X,
              NodeEditorView.CASCADE_START_Y + (cascade / NodeEditorView.CASCADE_COLUMNS) * NodeEditorView.CASCADE_STEP_Y)
            cascade += 1
        }
      }
      canvas.addActor(actor)
    }

    displayedNodes --= toRemove
    displayedNodes ++= toAdd
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

  /** 在空画布上右键弹出节点创建菜单。 */
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

  //FIXME：为了取名字 create(i) 会真的建节点，NGF 会顺带消耗 id；应提供只取名字的接口。
  private def buildNodeMenu(): Unit = {
    val c = App.root.getFrontendSession
    if (c != null) {
      val ids: IdSource = c.idSource
      for (i <- 0 until App.nodeRegistry.getCount) {
        val name: String = App.nodeRegistry.create(i)(using ids).name
        nodeMenu.addItem(new MenuItem(name, new ChangeListenerX(() => addNode(App.nodeRegistry.create(i)(using ids))(using ids))))
      }
    }
  }

  private def addNode(node: Node)(using ids: IdSource): Unit = {
    if (!graph.contains(node)) {
      update(graph.add(node))
      spawnPositions.put(node, new Vector2(spawnPos))
      markDirty()
    }
  }

  override def draw(batch: Batch, parentAlpha: Float): Unit = {
    val drawer = App.root.shapeDrawer
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
  private val GRID_SPACING: Float = 50f
  private val MIN_GRID_PIXEL: Float = 8f
  private val SINK_OFFSET_X: Float = 240f
  private val CASCADE_START_X: Float = 40f
  private val CASCADE_START_Y: Float = 40f
  private val CASCADE_COLUMNS: Int = 3
  private val CASCADE_STEP_X: Float = 220f
  private val CASCADE_STEP_Y: Float = 160f
}
