package com.lomekwi.cave.ui.node

import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.lomekwi.cave.app.App
import com.lomekwi.cave.pipeline.Node
import com.lomekwi.cave.ui.Colors
import space.earlygrey.shapedrawer.ShapeDrawer

import scala.jdk.CollectionConverters.*

/**
 * 端口圆点。圆点位置由 {@link #getAnchor} 单一定义，绘制与拖拽都以它为端点。
 */
trait PortActor extends Actor with PortHolder {
  private final val anchorTmp: Vector2 = new Vector2()
  private final val peerTmp: Vector2 = new Vector2()
  private final val cursor: Vector2 = new Vector2()
  private var dragging: Boolean = false
  private var detached: Boolean = false
  private var pendingPeer: PortActor = null

  /**
   * 圆点在绘制坐标系中的位置。
   */
  def getAnchor(out: Vector2): Vector2

  addListener(new InputListener {
    override def touchDown(event: InputEvent, x: Float, y: Float, pointer: Int, button: Int): Boolean = {
      if (button != Input.Buttons.LEFT || !isDotAt(x, y)) {
        false
      } else {
        // 圆点上的按下不应冒泡到卡片，否则会同时拖动节点
        event.stop()
        dragging = true
        detached = false
        pendingPeer = null
        setCursor(event.getStageX, event.getStageY)
        true
      }
    }

    override def touchDragged(event: InputEvent, x: Float, y: Float, pointer: Int): Unit = {
      if (dragging) {
        if (!detached) {
          detached = true
          pendingPeer = detachForDrag()
        }
        setCursor(event.getStageX, event.getStageY)
      }
    }

    override def touchUp(event: InputEvent, x: Float, y: Float, pointer: Int, button: Int): Unit = {
      if (dragging) {
        dragging = false
        if (detached) {
          detached = false
          dropLink(event.getStageX, event.getStageY)
        }
      }
    }
  })

  override def hit(x: Float, y: Float, touchable: Boolean): Actor = {
    // 容器与表格默认是 childrenOnly，圆点仍需能被直接命中，因此只排除 disabled
    if (touchable && (getTouchable eq Touchable.disabled)) {
      null
    } else if (isDotAt(x, y)) {
      this
    } else {
      super.hit(x, y, touchable)
    }
  }

  /** 绘制圆点。 */
  protected def drawDot(drawer: ShapeDrawer): Unit = {
    getAnchor(anchorTmp)
    drawer.filledCircle(anchorTmp.x, anchorTmp.y, PortActor.DOT_RADIUS, Color.WHITE)
  }

  /** 绘制拖拽中的临时连线。起点是被拖连接的固定端，没有固定端时从本圆点引出。 */
  protected def drawPendingLink(drawer: ShapeDrawer): Unit = {
    if (detached) {
      if (pendingPeer == null) {
        drawCurve(drawer, getAnchor(anchorTmp), cursor, Colors.ACCENT_LIGHT)
      } else {
        drawCurve(drawer, peerAnchor(pendingPeer, anchorTmp), cursor, Colors.ACCENT_LIGHT)
      }
    }
  }

  /**
   * 开始拖拽连线时摘下端口上已有的连接，返回该连接另一端的圆点，作为拖拽线的固定端，
   * 使整条线看起来是被从原地提起来。默认不摘，返回 null，拖拽线从本圆点引出。
   */
  protected def detachForDrag(): PortActor = null

  /** 绘制从自身圆点到另一个端口圆点的连线。 */
  protected def drawLink(drawer: ShapeDrawer, peer: PortActor): Unit = {
    getAnchor(anchorTmp)
    drawCurve(drawer, anchorTmp, peerAnchor(peer, peerTmp), Colors.ACCENT)
  }

  /** 另一个端口圆点的 anchor，折算到当前绘制坐标系。 */
  private def peerAnchor(peer: PortActor, out: Vector2): Vector2 = {
    peer.getAnchor(out)
    peer.getParent.localToStageCoordinates(out)
    toDrawingFrame(out)
  }

  /** 绘制从 start 到 end 的三次贝塞尔曲线，两端沿水平方向引出。 */
  private def drawCurve(drawer: ShapeDrawer, start: Vector2, end: Vector2, color: Color): Unit = {
    val ctrl: Float = Math.max(PortActor.MIN_CTRL_OFFSET, Math.abs(end.x - start.x) * 0.5f)
    val ctrlStartX: Float = start.x + ctrl
    val ctrlEndX: Float = end.x - ctrl
    var prevX: Float = start.x
    var prevY: Float = start.y
    var i: Int = 1
    while (i <= PortActor.CURVE_SEGMENTS) {
      val t: Float = i.toFloat / PortActor.CURVE_SEGMENTS
      val u: Float = 1f - t
      val w0: Float = u * u * u
      val w1: Float = 3f * u * u * t
      val w2: Float = 3f * u * t * t
      val w3: Float = t * t * t
      val x: Float = w0 * start.x + w1 * ctrlStartX + w2 * ctrlEndX + w3 * end.x
      val y: Float = w0 * start.y + w1 * start.y + w2 * end.y + w3 * end.y
      drawer.line(prevX, prevY, x, y, color, PortActor.LINE_THICKNESS)
      prevX = x
      prevY = y
      i += 1
    }
  }

  /** 在所在画布中查找承载指定端口的端口圆点；端口可能属于另一张卡片。 */
  protected def findPortActor(port: Node.Port): PortActor = {
    val owner: NodeActor = findNodeActor
    if (owner == null) {
      return null
    }
    owner.getParent match {
      case canvas: Group =>
        for (child <- canvas.getChildren.asScala) {
          child match {
            case card: NodeActor =>
              val actor: PortActor = card.getPortActor(port)
              if (actor != null) {
                return actor
              }
            case _ =>
          }
        }
      case _ =>
    }
    null
  }

  private def findNodeActor: NodeActor = {
    var current: Actor = getParent
    while (current != null) {
      current match {
        case card: NodeActor => return card
        case _ => current = current.getParent
      }
    }
    null
  }

  private def isDotAt(x: Float, y: Float): Boolean = {
    getAnchor(anchorTmp)
    parentToLocalCoordinates(anchorTmp)
    anchorTmp.dst(x, y) <= PortActor.GRAB_RADIUS
  }

  private def setCursor(stageX: Float, stageY: Float): Unit = {
    cursor.set(stageX, stageY)
    toDrawingFrame(cursor)
  }

  /**
   * 绘制坐标系：绘制时批次所在的坐标系。分组默认关闭变换，改为把自身 x/y 折算进子 actor，
   * 因此坐标系由更上层第一个开启变换的祖先决定。
   */
  private def drawingFrame: Group = {
    var current: Group = getParent
    while (current != null && !current.isTransform) {
      current = current.getParent
    }
    current
  }

  private def toDrawingFrame(point: Vector2): Vector2 = {
    val frame = drawingFrame
    if (frame == null) {
      point
    } else {
      frame.stageToLocalCoordinates(point)
    }
  }

  private def dropLink(stageX: Float, stageY: Float): Unit = {
    val stage = getStage
    if (stage != null) {
      val target: PortActor = portActorAt(stage.hit(stageX, stageY, true))
      if (target != null) {
        getPort.link(target.getPort)
      }
    }
  }

  /** 命中的 actor 自身或其祖先中的端口圆点。 */
  private def portActorAt(target: Actor): PortActor = {
    var current: Actor = target
    while (current != null) {
      current match {
        case actor: PortActor => return actor
        case _ => current = current.getParent
      }
    }
    null
  }
}

object PortActor {
  private final val DOT_RADIUS: Float = 5f
  /** 圆点的可抓取半径，比圆点本身大，便于命中。 */
  private final val GRAB_RADIUS: Float = 9f
  private final val MIN_CTRL_OFFSET: Float = 30f
  private final val CURVE_SEGMENTS: Int = 24
  private final val LINE_THICKNESS: Float = 2f
}
