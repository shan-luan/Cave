package com.lomekwi.cave.ui.editpanel.previewarea

import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Cursor
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.lomekwi.cave.app.selection.Selectable
import com.lomekwi.cave.app.App
import com.lomekwi.cave.task.ExportOptionsSet
import com.lomekwi.cave.pipeline.Filter
import com.lomekwi.cave.pipeline.Frame
import com.lomekwi.cave.pipeline.Source
import com.lomekwi.cave.pipeline.image.Transform
import com.lomekwi.cave.pipeline.image.Transformable
import com.lomekwi.cave.pipeline.image.TransNode
import com.lomekwi.cave.project.Project
import com.lomekwi.cave.timeline.Segment
import com.lomekwi.cave.timeline.UndoManager
import com.lomekwi.cave.timeline.playback.RefreshRequestEvent
import com.lomekwi.cave.ui.Colors
import space.earlygrey.shapedrawer.ShapeDrawer


import scala.compiletime.uninitialized
import scala.jdk.CollectionConverters.*
import java.util

class TransFrameActor(frame0: Frame & Transformable) extends Actor with Selectable {
  private var frame: Frame = uninitialized
  private var transformable: Transformable = uninitialized

  private var selected: Boolean = false

  private var dragModifier: TransNode = uninitialized
  private var dragging: Boolean = false
  private var startCanvasX: Float = 0
  private var startCanvasY: Float = 0
  private var startNodeDx: Float = 0
  private var startNodeDy: Float = 0
  private var dragCos: Float = 0
  private var dragSin: Float = 0
  private var dragScaleX: Float = 0
  private var dragScaleY: Float = 0
  private var dragFlipX: Boolean = false
  private var dragFlipY: Boolean = false

  private var gizmoDragging: Boolean = false
  private var gizmoHandle: Gizmo.Handle = uninitialized
  private final val gizmo: Gizmo = new Gizmo()
  private var gizmoStartW: Float = 0
  private var gizmoStartH: Float = 0
  private var gizmoStartDx: Float = 0
  private var gizmoStartDy: Float = 0
  private var gizmoStartScaleX: Float = 0
  private var gizmoStartScaleY: Float = 0
  private var gizmoStartRotation: Float = 0
  private var gizmoStartAngle: Float = 0
  private var gizmoAnchorLocalX: Float = 0
  private var gizmoAnchorLocalY: Float = 0
  private var gizmoAnchorStageX: Float = 0
  private var gizmoAnchorStageY: Float = 0
  private var gizmoCos: Float = 0
  private var gizmoSin: Float = 0
  private var gizmoFlipX: Boolean = false
  private var gizmoFlipY: Boolean = false
  private var gizmoOldState: UndoManager.TransNodeState = uninitialized

  private var myStartBBox: Array[Float] = uninitialized
  private var siblingBBoxes: util.List[Array[Float]] = uninitialized

  /** 移动吸附时的提示线位置（画布坐标），NaN 表示无吸附 */
  private var snapLineX: Float = Float.NaN
  private var snapLineY: Float = Float.NaN

  this.frame = frame0
  this.transformable = frame0
  addListener(new InputListener {
    override def touchDown(event: InputEvent, x: Float, y: Float, pointer: Int, button: Int): Boolean = {
      if (button != 0 || pointer != 0) return false
      val source = frame.getSource
      if (source == null) return false

      val handle = gizmo.hitHandle(event.getStageX, event.getStageY)
      if (handle != null && selected) {
        startGizmoDrag(source, handle, event.getStageX, event.getStageY)
        return true
      }

      dragModifier = TransFrameActor.findOrCreateTransNode(source)
      startNodeDx = dragModifier.getDx.toFloat
      startNodeDy = dragModifier.getDy.toFloat
      val p = getParent
      startCanvasX = (event.getStageX - p.getX) / p.getScaleX
      startCanvasY = (event.getStageY - p.getY) / p.getScaleY
      computeDragContext()
      captureSnapData()
      dragging = false
      true
    }

    override def touchDragged(event: InputEvent, x: Float, y: Float, pointer: Int): Unit = {
      if (gizmoDragging) {
        updateGizmoDrag(event.getStageX, event.getStageY)
      } else if (dragModifier != null) {
        dragging = true
        updateDrag(event.getStageX, event.getStageY)
      }
    }

    override def touchUp(event: InputEvent, x: Float, y: Float, pointer: Int, button: Int): Unit = {
      if (gizmoDragging) {
        finishGizmoDrag()
        return
      }
      if (dragModifier != null && dragging) {
        val p: Project = App.root.getFrontendProject
        if (p != null) {
          val node: TransNode = dragModifier
          val oldDx: Float = startNodeDx
          val oldDy: Float = startNodeDy
          val newDx: Float = node.getDx.toFloat
          val newDy: Float = node.getDy.toFloat
          val oldScaleX: Float = node.getScaleX.toFloat
          val oldScaleY: Float = node.getScaleY.toFloat
          val newScaleX: Float = gizmoStartScaleX
          val newScaleY: Float = gizmoStartScaleY
          val oldRotation: Float = gizmoStartRotation
          val newRotation: Float = node.getDRotation.toFloat
          val oldFlipX: Boolean = gizmoFlipX
          val oldFlipY: Boolean = gizmoFlipY
          val newFlipX: Boolean = node.flipX()
          val newFlipY: Boolean = node.flipY()
          p.undoManager.record(UndoManager.TransformNodeCommand(frame.getSource, node,
            UndoManager.TransNodeState(oldDx, oldDy, oldScaleX, oldScaleY, oldRotation, oldFlipX, oldFlipY),
            UndoManager.TransNodeState(newDx, newDy, newScaleX, newScaleY, newRotation, newFlipX, newFlipY)))
          p.projEventBus.post(RefreshRequestEvent)
        }
      }
      if (dragModifier != null && !dragging && !gizmoDragging) {
        val segment: Segment = if (frame.getSource != null) frame.getSource.getSegment else null
        if (segment != null && segment.getTrack != null) {
          val editPanel = App.root.getFrontendEditPanel
          if (editPanel != null) {
            val addToSelection = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)
            editPanel.getTimelineView.selectSegment(segment, addToSelection)
          }
        }
      }
      dragModifier = null
      dragging = false
      gizmoDragging = false
      gizmoHandle = null
      myStartBBox = null
      siblingBBoxes = null
      snapLineX = Float.NaN
      snapLineY = Float.NaN
    }

    override def enter(event: InputEvent, x: Float, y: Float, pointer: Int, fromActor: Actor): Unit = {
      gizmo.updateCursor(event.getStageX, event.getStageY)
    }

    override def exit(event: InputEvent, x: Float, y: Float, pointer: Int, toActor: Actor): Unit = {
      if (gizmo.hoveredHandle != null) {
        gizmo.hoveredHandle = null
        gizmo.setCursor(null)
      }
    }

    override def mouseMoved(event: InputEvent, x: Float, y: Float): Boolean = {
      gizmo.updateCursor(event.getStageX, event.getStageY)
      false
    }
  })

  def rebind[T <: Frame & Transformable](frame: T): Unit = {
    this.frame = frame
    this.transformable = frame
  }

  override def draw(batch: Batch, parentAlpha: Float): Unit = {
    super.draw(batch, parentAlpha)
    transformable.render(batch)
    if (selected) {
      TransFrameActor.tmpMatrix.set(batch.getTransformMatrix)
      batch.setTransformMatrix(TransFrameActor.IDENTITY)
      gizmo.draw(gizmoHandle)
      batch.setTransformMatrix(TransFrameActor.tmpMatrix)
    }
  }

  override def act(delta: Float): Unit = {
    super.act(delta)

    val transform = transformable.getTransform
    val scaleX = transform.getScaleX
    val scaleY = transform.getScaleY
    val w = transformable.getBaseWidth * scaleX
    val h = transformable.getBaseHeight * scaleY

    setPosition(transform.getX, transform.getY)
    setSize(w, h)
    setOrigin(w / 2, h / 2)
    setRotation(transform.getRotation)
    setScaleX(if (transform.isFlipX) -1f else 1f)
    setScaleY(if (transform.isFlipY) -1f else 1f)

    if (dragModifier != null && getParent != null && getStage != null) {
      TransFrameActor.dragStagePos.set(Gdx.input.getX.toFloat, Gdx.input.getY.toFloat)
      getStage.screenToStageCoordinates(TransFrameActor.dragStagePos)
      if (gizmoDragging) {
        updateGizmoDrag(TransFrameActor.dragStagePos.x, TransFrameActor.dragStagePos.y)
      } else if (dragging) {
        updateDrag(TransFrameActor.dragStagePos.x, TransFrameActor.dragStagePos.y)
      }
    }
  }

  override def hit(x: Float, y: Float, touchable: Boolean): Actor = {
    if (!touchable || !isTouchable) null
    else if (insidePreviewHitRegion(x, y)) this
    else null
  }

  /**
   * 判断事件点(x, y)是否落在「本Actor在stage坐标下的AABB 与 预览区域AABB」的交集内。
   * 仅处理交集内的事件，避免 TransFrameActor 抢夺预览区域之外的事件。
   */
  private def insidePreviewHitRegion(x: Float, y: Float): Boolean = {
    val w = getWidth
    val h = getHeight

    var handleOff: Float = 0
    if (selected) {
      handleOff = TransFrameActor.ROTATE_OFFSET_LOCAL
      val p = getParent
      if (p != null) {
        val ps = Math.min(Math.abs(p.getScaleX), Math.abs(p.getScaleY))
        if (ps > 0.0001f) handleOff /= ps
      }
    }

    val extLeft = -handleOff
    val extRight = w + handleOff
    val extBottom = -handleOff
    val extTop = h + handleOff
    if (x < extLeft || x >= extRight || y < extBottom || y >= extTop) return false

    val parent = getParent
    val grand = if (parent != null) parent.getParent else null
    if (!grand.isInstanceOf[PreviewArea]) return true
    val preview = grand.asInstanceOf[PreviewArea]

    TransFrameActor.hitCorner1.set(extLeft, extBottom)
    TransFrameActor.hitCorner2.set(extRight, extBottom)
    TransFrameActor.hitCorner3.set(extLeft, extTop)
    TransFrameActor.hitCorner4.set(extRight, extTop)
    localToStageCoordinates(TransFrameActor.hitCorner1)
    localToStageCoordinates(TransFrameActor.hitCorner2)
    localToStageCoordinates(TransFrameActor.hitCorner3)
    localToStageCoordinates(TransFrameActor.hitCorner4)
    TransFrameActor.actorBounds.set(Math.min(Math.min(TransFrameActor.hitCorner1.x, TransFrameActor.hitCorner2.x), Math.min(TransFrameActor.hitCorner3.x, TransFrameActor.hitCorner4.x)),
      Math.min(Math.min(TransFrameActor.hitCorner1.y, TransFrameActor.hitCorner2.y), Math.min(TransFrameActor.hitCorner3.y, TransFrameActor.hitCorner4.y)),
      Math.max(Math.max(TransFrameActor.hitCorner1.x, TransFrameActor.hitCorner2.x), Math.max(TransFrameActor.hitCorner3.x, TransFrameActor.hitCorner4.x)) - Math.min(Math.min(TransFrameActor.hitCorner1.x, TransFrameActor.hitCorner2.x), Math.min(TransFrameActor.hitCorner3.x, TransFrameActor.hitCorner4.x)),
      Math.max(Math.max(TransFrameActor.hitCorner1.y, TransFrameActor.hitCorner2.y), Math.max(TransFrameActor.hitCorner3.y, TransFrameActor.hitCorner4.y)) - Math.min(Math.min(TransFrameActor.hitCorner1.y, TransFrameActor.hitCorner2.y), Math.min(TransFrameActor.hitCorner3.y, TransFrameActor.hitCorner4.y)))

    TransFrameActor.hitCorner1.set(0f, 0f)
    TransFrameActor.hitCorner2.set(preview.getWidth, preview.getHeight)
    preview.localToStageCoordinates(TransFrameActor.hitCorner1)
    preview.localToStageCoordinates(TransFrameActor.hitCorner2)
    TransFrameActor.previewBounds.set(Math.min(TransFrameActor.hitCorner1.x, TransFrameActor.hitCorner2.x),
      Math.min(TransFrameActor.hitCorner1.y, TransFrameActor.hitCorner2.y),
      Math.abs(TransFrameActor.hitCorner2.x - TransFrameActor.hitCorner1.x),
      Math.abs(TransFrameActor.hitCorner2.y - TransFrameActor.hitCorner1.y))

    TransFrameActor.intersectBounds.set(TransFrameActor.actorBounds)
    TransFrameActor.intersectBounds.x = Math.max(TransFrameActor.actorBounds.x, TransFrameActor.previewBounds.x)
    TransFrameActor.intersectBounds.y = Math.max(TransFrameActor.actorBounds.y, TransFrameActor.previewBounds.y)
    TransFrameActor.intersectBounds.width = Math.min(TransFrameActor.actorBounds.x + TransFrameActor.actorBounds.width, TransFrameActor.previewBounds.x + TransFrameActor.previewBounds.width) - TransFrameActor.intersectBounds.x
    TransFrameActor.intersectBounds.height = Math.min(TransFrameActor.actorBounds.y + TransFrameActor.actorBounds.height, TransFrameActor.previewBounds.y + TransFrameActor.previewBounds.height) - TransFrameActor.intersectBounds.y
    if (TransFrameActor.intersectBounds.width <= 0 || TransFrameActor.intersectBounds.height <= 0) return false

    TransFrameActor.hitCorner1.set(x, y)
    localToStageCoordinates(TransFrameActor.hitCorner1)
    TransFrameActor.intersectBounds.contains(TransFrameActor.hitCorner1.x, TransFrameActor.hitCorner1.y)
  }

  private def localToParent(lx: Float, ly: Float, out: Vector2): Unit = {
    val ox = getOriginX
    val oy = getOriginY
    val rad = Math.toRadians(getRotation.toDouble).toFloat
    val cos = Math.cos(rad.toDouble).toFloat
    val sin = Math.sin(rad.toDouble).toFloat
    val sx = getScaleX
    val sy = getScaleY

    val dx = lx - ox
    val dy = ly - oy
    val rx = dx * cos * sx - dy * sin * sy
    val ry = dx * sin * sx + dy * cos * sy
    out.x = getX + ox + rx
    out.y = getY + oy + ry
  }

  private def clampToAnchor(value: Float, anchor: Float, handle: Gizmo.Handle, isX: Boolean): Float = {
    handle match {
      case Gizmo.Handle.NW => if (isX) Math.min(value, anchor) else Math.max(value, anchor)
      case Gizmo.Handle.NE => Math.max(value, anchor)
      case Gizmo.Handle.SE => if (isX) Math.max(value, anchor) else Math.min(value, anchor)
      case Gizmo.Handle.SW => Math.min(value, anchor)
      case Gizmo.Handle.N => if (isX) value else Math.max(value, anchor)
      case Gizmo.Handle.S => if (isX) value else Math.min(value, anchor)
      case Gizmo.Handle.E => if (isX) Math.max(value, anchor) else value
      case Gizmo.Handle.W => if (isX) Math.min(value, anchor) else value
      case _ => value
    }
  }

  private def startGizmoDrag(source: Source[?], handle: Gizmo.Handle, stageX: Float, stageY: Float): Unit = {
    gizmoHandle = handle
    gizmoDragging = true

    dragModifier = TransFrameActor.findOrCreateTransNode(source)
    gizmoStartW = getWidth
    gizmoStartH = getHeight
    gizmoStartDx = dragModifier.getDx.toFloat
    gizmoStartDy = dragModifier.getDy.toFloat
    gizmoStartScaleX = dragModifier.getScaleX.toFloat
    gizmoStartScaleY = dragModifier.getScaleY.toFloat
    gizmoStartRotation = dragModifier.getDRotation.toFloat
    gizmoOldState = UndoManager.TransNodeState(
      gizmoStartDx, gizmoStartDy,
      gizmoStartScaleX, gizmoStartScaleY,
      gizmoStartRotation,
      dragModifier.flipX(), dragModifier.flipY())

    computeDragContext()

    handle match {
      case Gizmo.Handle.NW =>
        gizmoAnchorLocalX = getWidth
        gizmoAnchorLocalY = 0f
      case Gizmo.Handle.N =>
        gizmoAnchorLocalX = getWidth / 2f
        gizmoAnchorLocalY = 0f
      case Gizmo.Handle.NE =>
        gizmoAnchorLocalX = 0f
        gizmoAnchorLocalY = 0f
      case Gizmo.Handle.E =>
        gizmoAnchorLocalX = 0f
        gizmoAnchorLocalY = getHeight / 2f
      case Gizmo.Handle.SE =>
        gizmoAnchorLocalX = 0f
        gizmoAnchorLocalY = getHeight
      case Gizmo.Handle.S =>
        gizmoAnchorLocalX = getWidth / 2f
        gizmoAnchorLocalY = getHeight
      case Gizmo.Handle.SW =>
        gizmoAnchorLocalX = getWidth
        gizmoAnchorLocalY = getHeight
      case Gizmo.Handle.W =>
        gizmoAnchorLocalX = getWidth
        gizmoAnchorLocalY = getHeight / 2f
      case Gizmo.Handle.ROTATE =>
    }

    // 锚点在 stage 坐标下的固定位置
    TransFrameActor.tmp1.set(gizmoAnchorLocalX, gizmoAnchorLocalY)
    localToStageCoordinates(TransFrameActor.tmp1)
    gizmoAnchorStageX = TransFrameActor.tmp1.x
    gizmoAnchorStageY = TransFrameActor.tmp1.y

    // 总变换（含 dragModifier 自身的旋转/翻转）
    val totalRad = Math.toRadians(getRotation.toDouble).toFloat
    gizmoCos = Math.cos(totalRad.toDouble).toFloat
    gizmoSin = Math.sin(totalRad.toDouble).toFloat
    gizmoFlipX = getScaleX < 0
    gizmoFlipY = getScaleY < 0

    if (handle == Gizmo.Handle.ROTATE) {
      val centerStagePos = TransFrameActor.tmp1
      centerStagePos.set(getWidth / 2f, getHeight / 2f)
      localToStageCoordinates(centerStagePos)
      gizmoStartAngle = Math.toDegrees(Math.atan2((stageY - centerStagePos.y).toDouble, (stageX - centerStagePos.x).toDouble)).toFloat
    }
  }

  private def updateGizmoDrag(stageX: Float, stageY: Float): Unit = {
    if (gizmoHandle == Gizmo.Handle.ROTATE) {
      updateRotateDrag(stageX, stageY)
      return
    }

    // stage 坐标取差后换算为画布本地 delta（canvas 仅平移+均匀缩放）
    val p = getParent
    val canvasDeltaX = if (p != null) (stageX - gizmoAnchorStageX) / p.getScaleX else stageX - gizmoAnchorStageX
    val canvasDeltaY = if (p != null) (stageY - gizmoAnchorStageY) / p.getScaleY else stageY - gizmoAnchorStageY

    // 画布空间 → 本地空间（用含 dragModifier 的总变换）
    var dLocalX = canvasDeltaX * gizmoCos + canvasDeltaY * gizmoSin
    var dLocalY = -canvasDeltaX * gizmoSin + canvasDeltaY * gizmoCos
    if (gizmoFlipX) dLocalX = -dLocalX
    if (gizmoFlipY) dLocalY = -dLocalY

    var localX = gizmoAnchorLocalX + dLocalX
    var localY = gizmoAnchorLocalY + dLocalY

    localX = clampToAnchor(localX, gizmoAnchorLocalX, gizmoHandle, true)
    localY = clampToAnchor(localY, gizmoAnchorLocalY, gizmoHandle, false)

    val freeScale = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)

    var newScaleX = gizmoStartScaleX
    var newScaleY = gizmoStartScaleY

    gizmoHandle match {
      case Gizmo.Handle.NW | Gizmo.Handle.NE | Gizmo.Handle.SE | Gizmo.Handle.SW =>
        val anchorX = gizmoAnchorLocalX
        val anchorY = gizmoAnchorLocalY
        if (Math.abs(anchorX - localX) < 0.01f) localX = anchorX + 0.01f
        if (Math.abs(anchorY - localY) < 0.01f) localY = anchorY + 0.01f
        var scaleW = Math.abs(anchorX - localX) / gizmoStartW
        var scaleH = Math.abs(anchorY - localY) / gizmoStartH
        scaleW = Math.max(TransFrameActor.MIN_SCALE, scaleW)
        scaleH = Math.max(TransFrameActor.MIN_SCALE, scaleH)
        if (!freeScale) {
          val s = Math.sqrt((scaleW * scaleH).toDouble).toFloat
          scaleW = s
          scaleH = s
        }
        newScaleX = gizmoStartScaleX * scaleW
        newScaleY = gizmoStartScaleY * scaleH
      case Gizmo.Handle.N | Gizmo.Handle.S =>
        val topY = if (gizmoHandle == Gizmo.Handle.N) localY else gizmoAnchorLocalY
        val bottomY = if (gizmoHandle == Gizmo.Handle.S) localY else gizmoAnchorLocalY
        val newH = Math.max(TransFrameActor.MIN_SIZE, Math.abs(topY - bottomY))
        var scaleH1 = newH / gizmoStartH
        scaleH1 = Math.max(TransFrameActor.MIN_SCALE, scaleH1)
        newScaleY = gizmoStartScaleY * scaleH1
      case Gizmo.Handle.E | Gizmo.Handle.W =>
        val rightX = if (gizmoHandle == Gizmo.Handle.E) localX else gizmoAnchorLocalX
        val leftX = if (gizmoHandle == Gizmo.Handle.W) localX else gizmoAnchorLocalX
        val newW = Math.max(TransFrameActor.MIN_SIZE, Math.abs(rightX - leftX))
        var scaleW1 = newW / gizmoStartW
        scaleW1 = Math.max(TransFrameActor.MIN_SCALE, scaleW1)
        newScaleX = gizmoStartScaleX * scaleW1
      case Gizmo.Handle.ROTATE =>
    }

    dragModifier.setScaleX(newScaleX)
    dragModifier.setScaleY(newScaleY)

    val scaleChangeW = newScaleX / gizmoStartScaleX
    val scaleChangeH = newScaleY / gizmoStartScaleY

    // 锚点补偿（画布空间）：锚点相对中心偏移随缩放变化，扣掉中心位移后保持锚点不动
    val halfW = gizmoStartW * 0.5f
    val halfH = gizmoStartH * 0.5f
    val compX = (scaleChangeW - 1f) * (halfW - gizmoAnchorLocalX)
    val compY = (scaleChangeH - 1f) * (halfH - gizmoAnchorLocalY)
    var rotCompX = gizmoCos * compX - gizmoSin * compY
    var rotCompY = gizmoSin * compX + gizmoCos * compY
    if (gizmoFlipX) rotCompX = -rotCompX
    if (gizmoFlipY) rotCompY = -rotCompY
    val posDeltaX = rotCompX - (scaleChangeW - 1f) * halfW
    val posDeltaY = rotCompY - (scaleChangeH - 1f) * halfH

    // 画布位移 → dragModifier 本地位移（仅用其之前的变换）
    var ddx = (posDeltaX * dragCos + posDeltaY * dragSin) / dragScaleX
    var ddy = (-posDeltaX * dragSin + posDeltaY * dragCos) / dragScaleY
    if (dragFlipX) ddx = -ddx
    if (dragFlipY) ddy = -ddy

    dragModifier.setDx(gizmoStartDx + ddx)
    dragModifier.setDy(gizmoStartDy + ddy)

    applyModifiers()
  }

  private def updateRotateDrag(stageX: Float, stageY: Float): Unit = {
    val centerStagePos = TransFrameActor.tmp1
    centerStagePos.set(getWidth / 2f, getHeight / 2f)
    localToStageCoordinates(centerStagePos)
    val currentAngle = Math.toDegrees(Math.atan2((stageY - centerStagePos.y).toDouble, (stageX - centerStagePos.x).toDouble)).toFloat
    var delta = currentAngle - gizmoStartAngle

    if (delta > 180) delta -= 360
    if (delta < -180) delta += 360

    val snap = Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT)
    if (snap) {
      delta = Math.round(delta / 15f) * 15f
    }

    dragModifier.setDRotation(gizmoStartRotation + delta)
    applyModifiers()
  }

  private def finishGizmoDrag(): Unit = {
    val p: Project = App.root.getFrontendProject
    if (p != null && dragModifier != null && gizmoOldState != null) {
      val node: TransNode = dragModifier
      val newState = UndoManager.TransNodeState(
        node.getDx.toFloat, node.getDy.toFloat,
        node.getScaleX.toFloat, node.getScaleY.toFloat,
        node.getDRotation.toFloat,
        node.flipX(), node.flipY())
      if (!gizmoOldState.equals(newState)) {
        p.undoManager.record(UndoManager.TransformNodeCommand(
          frame.getSource, node, gizmoOldState, newState))
      }
      p.projEventBus.post(RefreshRequestEvent)
    }
    gizmoDragging = false
    gizmoHandle = null
    dragModifier = null
    gizmoOldState = null
  }

  private def updateDrag(stageX: Float, stageY: Float): Unit = {
    val parent = getParent
    val canvasX = (stageX - parent.getX) / parent.getScaleX
    val canvasY = (stageY - parent.getY) / parent.getScaleY
    var dx = canvasX - startCanvasX
    var dy = canvasY - startCanvasY
    computeSnapAdjustment(dx, dy)
    dx += TransFrameActor.snapAdjust.x
    dy += TransFrameActor.snapAdjust.y
    var localDx = (dx * dragCos + dy * dragSin) / dragScaleX
    var localDy = (-dx * dragSin + dy * dragCos) / dragScaleY
    if (dragFlipX) localDx = -localDx
    if (dragFlipY) localDy = -localDy
    dragModifier.setDx(startNodeDx + localDx)
    dragModifier.setDy(startNodeDy + localDy)
    applyModifiers()
  }

  private def applyModifiers(): Unit = {
    transformable.reset()
    val source = frame.getSource
    if (source != null) {
      val filters: util.List[Filter[?]] = source.getFilters.asInstanceOf[util.List[Filter[?]]]
      filters.asScala.foreach {
        case node: TransNode => applyTransNode(node)
        case _ =>
      }
    }
  }

  private def applyTransNode(node: TransNode): Unit = {
    val target = transformable
    var t = target.getTransform
    if (t == null) {
      t = new Transform()
      target.setTransform(t)
    }
    t.applyLocal(node.getDx.toFloat, node.getDy.toFloat,
      node.getScaleX.toFloat, node.getScaleY.toFloat,
      node.getDRotation.toFloat, node.flipX(), node.flipY())
  }

  private def computeDragContext(): Unit = {
    val t = new Transform(0, 0, 0)
    val source = frame.getSource
    if (source != null) {
      val filters: util.List[Filter[?]] = source.getFilters.asInstanceOf[util.List[Filter[?]]]
      filters.asScala.takeWhile(f => !(f eq dragModifier)).foreach {
        case tf: TransNode =>
          t.applyLocal(tf.getDx.toFloat, tf.getDy.toFloat,
            tf.getScaleX.toFloat, tf.getScaleY.toFloat,
            tf.getDRotation.toFloat, tf.flipX(), tf.flipY())
        case _ =>
      }
    }
    dragScaleX = t.getScaleX
    dragScaleY = t.getScaleY
    if (dragScaleX < 0.0001f) dragScaleX = 1f
    if (dragScaleY < 0.0001f) dragScaleY = 1f
    dragFlipX = t.isFlipX
    dragFlipY = t.isFlipY
    val rotRad = t.getRotationRadians
    dragCos = Math.cos(rotRad.toDouble).toFloat
    dragSin = Math.sin(rotRad.toDouble).toFloat
  }

  private def captureSnapData(): Unit = {
    myStartBBox = computeCanvasBBox()
    siblingBBoxes = new util.ArrayList[Array[Float]]()
    getParent match {
      case g: com.badlogic.gdx.scenes.scene2d.Group =>
        g.getChildren.asScala.foreach {
          case other: TransFrameActor if other ne this =>
            siblingBBoxes.add(other.computeCanvasBBox())
          case _ =>
        }
      case _ =>
    }
    val set = ExportOptionsSet.load()
    for (opts <- set.presets.asScala) {
      if (!(opts.width <= 0 || opts.height <= 0)) {
        val l = 0f
        val r = opts.width.toFloat
        val b = 0f
        val t = opts.height.toFloat
        siblingBBoxes.add(Array(l, r, b, t, (l + r) * 0.5f, (b + t) * 0.5f))
      }
    }
  }

  private def computeCanvasBBox(): Array[Float] = {
    val w = getWidth
    val h = getHeight
    localToParent(0f, 0f, TransFrameActor.tmp1)
    localToParent(w, 0f, TransFrameActor.tmp2)
    var l = Math.min(TransFrameActor.tmp1.x, TransFrameActor.tmp2.x)
    var r = Math.max(TransFrameActor.tmp1.x, TransFrameActor.tmp2.x)
    var b = Math.min(TransFrameActor.tmp1.y, TransFrameActor.tmp2.y)
    var t = Math.max(TransFrameActor.tmp1.y, TransFrameActor.tmp2.y)
    localToParent(0f, h, TransFrameActor.tmp3)
    l = Math.min(l, TransFrameActor.tmp3.x)
    r = Math.max(r, TransFrameActor.tmp3.x)
    b = Math.min(b, TransFrameActor.tmp3.y)
    t = Math.max(t, TransFrameActor.tmp3.y)
    localToParent(w, h, TransFrameActor.tmp2)
    l = Math.min(l, TransFrameActor.tmp2.x)
    r = Math.max(r, TransFrameActor.tmp2.x)
    b = Math.min(b, TransFrameActor.tmp2.y)
    t = Math.max(t, TransFrameActor.tmp2.y)
    Array(l, r, b, t, (l + r) * 0.5f, (b + t) * 0.5f)
  }

  private def computeSnapAdjustment(dx: Float, dy: Float): Unit = {
    TransFrameActor.snapAdjust.set(0f, 0f)
    snapLineX = Float.NaN
    snapLineY = Float.NaN
    if (myStartBBox == null || siblingBBoxes == null) return
    val p = getParent
    val threshold = if (p != null) TransFrameActor.SNAP_THRESHOLD_SCREEN / p.getScaleX else TransFrameActor.SNAP_THRESHOLD_SCREEN

    val pl = myStartBBox(0) + dx
    val pr = myStartBBox(1) + dx
    val pb = myStartBBox(2) + dy
    val pt = myStartBBox(3) + dy
    val pcx = myStartBBox(4) + dx
    val pcy = myStartBBox(5) + dy

    var bestSnapX = 0f
    var bestSnapY = 0f
    var bestLineX = Float.NaN
    var bestLineY = Float.NaN
    var bestDistX = threshold
    var bestDistY = threshold

    for (s <- siblingBBoxes.asScala) {
      var d = s(0) - pl
      if (Math.abs(d) < bestDistX) {
        bestDistX = Math.abs(d)
        bestSnapX = d
        bestLineX = s(0)
      }
      d = s(1) - pl
      if (Math.abs(d) < bestDistX) {
        bestDistX = Math.abs(d)
        bestSnapX = d
        bestLineX = s(1)
      }
      d = s(1) - pr
      if (Math.abs(d) < bestDistX) {
        bestDistX = Math.abs(d)
        bestSnapX = d
        bestLineX = s(1)
      }
      d = s(0) - pr
      if (Math.abs(d) < bestDistX) {
        bestDistX = Math.abs(d)
        bestSnapX = d
        bestLineX = s(0)
      }
      d = s(4) - pcx
      if (Math.abs(d) < bestDistX) {
        bestDistX = Math.abs(d)
        bestSnapX = d
        bestLineX = s(4)
      }

      d = s(2) - pb
      if (Math.abs(d) < bestDistY) {
        bestDistY = Math.abs(d)
        bestSnapY = d
        bestLineY = s(2)
      }
      d = s(3) - pb
      if (Math.abs(d) < bestDistY) {
        bestDistY = Math.abs(d)
        bestSnapY = d
        bestLineY = s(3)
      }
      d = s(3) - pt
      if (Math.abs(d) < bestDistY) {
        bestDistY = Math.abs(d)
        bestSnapY = d
        bestLineY = s(3)
      }
      d = s(2) - pt
      if (Math.abs(d) < bestDistY) {
        bestDistY = Math.abs(d)
        bestSnapY = d
        bestLineY = s(2)
      }
      d = s(5) - pcy
      if (Math.abs(d) < bestDistY) {
        bestDistY = Math.abs(d)
        bestSnapY = d
        bestLineY = s(5)
      }
    }

    if (bestDistX < threshold) {
      TransFrameActor.snapAdjust.x = bestSnapX
      snapLineX = bestLineX
    }
    if (bestDistY < threshold) {
      TransFrameActor.snapAdjust.y = bestSnapY
      snapLineY = bestLineY
    }
  }

  override def isSelected: Boolean = {
    selected
  }

  def getSnapLineX: Float = {
    snapLineX
  }

  def getSnapLineY: Float = {
    snapLineY
  }

  override def setSelected(selected: Boolean): Unit = {
    this.selected = selected
    if (!selected) {
      gizmoDragging = false
      gizmoHandle = null
      gizmo.hoveredHandle = null
      if (Gdx.app.getType == Application.ApplicationType.Desktop) {
        Gdx.graphics.setSystemCursor(Cursor.SystemCursor.Arrow)
      }
    }
  }

  protected class Gizmo {
    private[TransFrameActor] var hoveredHandle: Gizmo.Handle = uninitialized

    private[TransFrameActor] def hitHandle(stageX: Float, stageY: Float): Gizmo.Handle = {
      val w = getWidth
      val h = getHeight
      if (w <= 0 || h <= 0) return null
      val hw = w / 2f
      val hh = h / 2f

      val localCoords = TransFrameActor.tmp1
      localCoords.set(stageX, stageY)
      stageToLocalCoordinates(localCoords)
      val lx = localCoords.x
      val ly = localCoords.y

      val localPositions: Array[Array[Float]] = Array(
        Array(0f, h), Array(hw, h), Array(w, h), Array(w, hh),
        Array(w, 0f), Array(hw, 0f), Array(0f, 0f), Array(0f, hh)
      )

      val r2 = Gizmo.HANDLE_HIT_RADIUS * Gizmo.HANDLE_HIT_RADIUS
      var i = 0
      while (i < 8) {
        val dx = lx - localPositions(i)(0)
        val dy = ly - localPositions(i)(1)
        if (dx * dx + dy * dy <= r2) {
          return Gizmo.Handle.values(i)
        }
        i += 1
      }

      val anchor = localToStageCoordinates(TransFrameActor.tmp2.set(hw, h))
      val refUp = localToStageCoordinates(TransFrameActor.tmp3.set(hw, h + 1f))
      var dirX = refUp.x - anchor.x
      var dirY = refUp.y - anchor.y
      var dirLen = Math.sqrt((dirX * dirX + dirY * dirY).toDouble).toFloat
      if (dirLen < 0.0001f) {
        dirX = 0f
        dirY = 1f
        dirLen = 1f
      }
      val hx = anchor.x + dirX / dirLen * TransFrameActor.ROTATE_OFFSET_LOCAL
      val hy = anchor.y + dirY / dirLen * TransFrameActor.ROTATE_OFFSET_LOCAL
      val dx = stageX - hx
      val dy = stageY - hy
      if (dx * dx + dy * dy <= r2) {
        return Gizmo.Handle.ROTATE
      }

      null
    }

    private[TransFrameActor] def updateCursor(stageX: Float, stageY: Float): Unit = {
      if (selected) {
        val h = hitHandle(stageX, stageY)
        if (h != hoveredHandle) {
          hoveredHandle = h
          setCursor(h)
        }
      }
    }

    private[TransFrameActor] def setCursor(handle: Gizmo.Handle): Unit = {
      if (Gdx.app.getType != Application.ApplicationType.Desktop) return
      if (handle == null) {
        Gdx.graphics.setSystemCursor(Cursor.SystemCursor.Arrow)
        return
      }
      if (handle == Gizmo.Handle.ROTATE) {
        Gdx.graphics.setSystemCursor(Cursor.SystemCursor.Hand)
        return
      }
      // 光标由锚点→手柄的屏幕方向决定，旋转/翻转后仍与视觉一致
      val w = getWidth
      val h = getHeight
      var hx: Float = 0f
      var hy: Float = 0f
      var ax: Float = 0f
      var ay: Float = 0f
      handle match {
        case Gizmo.Handle.NW =>
          hx = 0f
          hy = h
          ax = w
          ay = 0f
        case Gizmo.Handle.N =>
          hx = w / 2f
          hy = h
          ax = w / 2f
          ay = 0f
        case Gizmo.Handle.NE =>
          hx = w
          hy = h
          ax = 0f
          ay = 0f
        case Gizmo.Handle.E =>
          hx = w
          hy = h / 2f
          ax = 0f
          ay = h / 2f
        case Gizmo.Handle.SE =>
          hx = w
          hy = 0f
          ax = 0f
          ay = h
        case Gizmo.Handle.S =>
          hx = w / 2f
          hy = 0f
          ax = w / 2f
          ay = h
        case Gizmo.Handle.SW =>
          hx = 0f
          hy = 0f
          ax = w
          ay = h
        case Gizmo.Handle.W =>
          hx = 0f
          hy = h / 2f
          ax = w
          ay = h / 2f
        case _ =>
          hx = w
          hy = 0f
          ax = 0f
          ay = h
      }
      TransFrameActor.tmp1.set(ax, ay)
      TransFrameActor.tmp2.set(hx, hy)
      localToStageCoordinates(TransFrameActor.tmp1)
      localToStageCoordinates(TransFrameActor.tmp2)
      val dx = TransFrameActor.tmp2.x - TransFrameActor.tmp1.x
      val dy = TransFrameActor.tmp2.y - TransFrameActor.tmp1.y
      if (handle == Gizmo.Handle.NW || handle == Gizmo.Handle.NE || handle == Gizmo.Handle.SE || handle == Gizmo.Handle.SW) {
        if (dx * dy > 0) {
          Gdx.graphics.setSystemCursor(Cursor.SystemCursor.NESWResize)
        } else {
          Gdx.graphics.setSystemCursor(Cursor.SystemCursor.NWSEResize)
        }
      } else if (Math.abs(dx) > Math.abs(dy)) {
        Gdx.graphics.setSystemCursor(Cursor.SystemCursor.HorizontalResize)
      } else {
        Gdx.graphics.setSystemCursor(Cursor.SystemCursor.VerticalResize)
      }
    }

    private[TransFrameActor] def draw(activeHandle: Gizmo.Handle): Unit = {
      val w = getWidth
      val h = getHeight
      if (w <= 0 || h <= 0) return
      val hw = w / 2f
      val hh = h / 2f
      val sd: ShapeDrawer = App.root.getShapeDrawer
      val lineWidth = 2f
      val handleHalf = 6f
      val rotateRadius = 5f

      // 选中边框
      val bl = localToStageCoordinates(TransFrameActor.tmp1.set(0f, 0f))
      val br = localToStageCoordinates(TransFrameActor.tmp2.set(w, 0f))
      val tr = localToStageCoordinates(TransFrameActor.tmp3.set(w, h))
      val tl = localToStageCoordinates(TransFrameActor.dragStagePos.set(0f, h))
      sd.line(bl.x, bl.y, br.x, br.y, Colors.FRAME_OUTLINE, 2f)
      sd.line(br.x, br.y, tr.x, tr.y, Colors.FRAME_OUTLINE, 2f)
      sd.line(tr.x, tr.y, tl.x, tl.y, Colors.FRAME_OUTLINE, 2f)
      sd.line(tl.x, tl.y, bl.x, bl.y, Colors.FRAME_OUTLINE, 2f)

      // gizmo 线条
      var a = localToStageCoordinates(TransFrameActor.tmp1.set(0f, 0f))
      var b = localToStageCoordinates(TransFrameActor.tmp2.set(w, 0f))
      sd.line(a.x, a.y, b.x, b.y, Colors.FRAME_OUTLINE, lineWidth)
      a = localToStageCoordinates(TransFrameActor.tmp1.set(w, 0f))
      b = localToStageCoordinates(TransFrameActor.tmp2.set(w, h))
      sd.line(a.x, a.y, b.x, b.y, Colors.FRAME_OUTLINE, lineWidth)
      a = localToStageCoordinates(TransFrameActor.tmp1.set(w, h))
      b = localToStageCoordinates(TransFrameActor.tmp2.set(0f, h))
      sd.line(a.x, a.y, b.x, b.y, Colors.FRAME_OUTLINE, lineWidth)
      a = localToStageCoordinates(TransFrameActor.tmp1.set(0f, h))
      b = localToStageCoordinates(TransFrameActor.tmp2.set(0f, 0f))
      sd.line(a.x, a.y, b.x, b.y, Colors.FRAME_OUTLINE, lineWidth)
      a = localToStageCoordinates(TransFrameActor.tmp1.set(hw, h))
      val refUp = localToStageCoordinates(TransFrameActor.tmp2.set(hw, h + 1f))
      var dirX = refUp.x - a.x
      var dirY = refUp.y - a.y
      var dirLen = Math.sqrt((dirX * dirX + dirY * dirY).toDouble).toFloat
      if (dirLen < 0.0001f) {
        dirX = 0f
        dirY = 1f
        dirLen = 1f
      }
      val stickX = a.x + dirX / dirLen * TransFrameActor.ROTATE_OFFSET_LOCAL
      val stickY = a.y + dirY / dirLen * TransFrameActor.ROTATE_OFFSET_LOCAL
      sd.line(a.x, a.y, stickX, stickY, Colors.ACCENT, lineWidth)
      sd.filledCircle(stickX, stickY, rotateRadius, Colors.ACCENT)

      // 控制点
      for (handle <- Gizmo.Handle.values) {
        if (handle != Gizmo.Handle.ROTATE) {
          val hx: Float = handle match {
            case Gizmo.Handle.NW => 0f
            case Gizmo.Handle.N => hw
            case Gizmo.Handle.NE => w
            case Gizmo.Handle.E => w
            case Gizmo.Handle.SE => w
            case Gizmo.Handle.S => hw
            case Gizmo.Handle.SW => 0f
            case Gizmo.Handle.W => 0f
            case _ => 0f
          }
          val hy: Float = handle match {
            case Gizmo.Handle.NW => h
            case Gizmo.Handle.N => h
            case Gizmo.Handle.NE => h
            case Gizmo.Handle.E => hh
            case Gizmo.Handle.SE => 0f
            case Gizmo.Handle.S => 0f
            case Gizmo.Handle.SW => 0f
            case Gizmo.Handle.W => hh
            case _ => 0f
          }
          val hp = localToStageCoordinates(TransFrameActor.tmp1.set(hx, hy))
          val sx = hp.x
          val sy = hp.y
          val fill: Color = if (activeHandle == handle || hoveredHandle == handle)
            Colors.FRAME_OUTLINE else Colors.ACCENT
          sd.filledRectangle(sx - handleHalf, sy - handleHalf,
            handleHalf * 2, handleHalf * 2, fill)
          sd.rectangle(sx - handleHalf, sy - handleHalf,
            handleHalf * 2, handleHalf * 2, Colors.FRAME_OUTLINE, 1f)
        }
      }
    }
  }

  protected object Gizmo {
    private[TransFrameActor] final val HANDLE_HIT_RADIUS: Float = 18f

    enum Handle {
      case NW, N, NE, E, SE, S, SW, W, ROTATE
    }
  }
}

object TransFrameActor {
  private final val MIN_SCALE: Float = 0.01f
  private final val MIN_SIZE: Float = 4f
  final val ROTATE_OFFSET_LOCAL: Float = 70f
  private final val tmpMatrix: Matrix4 = new Matrix4()
  private final val IDENTITY: Matrix4 = new Matrix4().idt()

  private final val dragStagePos: Vector2 = new Vector2()
  private final val tmp1: Vector2 = new Vector2()
  private final val tmp2: Vector2 = new Vector2()
  private final val tmp3: Vector2 = new Vector2()
  private final val hitCorner1: Vector2 = new Vector2()
  private final val hitCorner2: Vector2 = new Vector2()
  private final val hitCorner3: Vector2 = new Vector2()
  private final val hitCorner4: Vector2 = new Vector2()
  private final val actorBounds: Rectangle = new Rectangle()
  private final val previewBounds: Rectangle = new Rectangle()
  private final val intersectBounds: Rectangle = new Rectangle()

  private final val SNAP_THRESHOLD_SCREEN: Float = 10f
  private final val snapAdjust: Vector2 = new Vector2()

  private def findOrCreateTransNode(source: Source[?]): TransNode = {
    val filters: util.List[Filter[?]] = source.getFilters.asInstanceOf[util.List[Filter[?]]]
    filters.asScala.reverseIterator.collectFirst { case tf: TransNode => tf }.getOrElse {
      val tf = new TransNode(0, 0, 1, 1, 0)
      source.asInstanceOf[Source[Frame]].attach(tf.asInstanceOf[Filter[? >: Frame]])
      tf
    }
  }
}
