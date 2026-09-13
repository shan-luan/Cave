package com.lomekwi.cave.ui.widget

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Cursor
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup
import com.badlogic.gdx.scenes.scene2d.utils.DragListener
import com.lomekwi.cave.app.App
import com.lomekwi.cave.ui.editpanel.tlarea.TlGroup

/**
 * 可平移/缩放的画布容器：内部持有 canvas Group，滚轮以光标为中心缩放，
 * 键盘焦点获得时按 SCROLL_* 热键平移。缩放由 zoom * baseScale 构成，
 * baseScale 供外部按视口尺寸适配（如预览区），默认 1。
 */
class PanZoomCanvas(private val minZoom: Float, private val maxZoom: Float, private val moveSpeed: Float) extends WidgetGroup {
  private final val canvas: Group = new Group()
  private var xOffset: Float = 0
  private var yOffset: Float = 0
  private var zoom: Float = 1f
  private var baseScale: Float = 1f
  private final val screenPos: Vector2 = new Vector2()

  def this() = {
    this(0.05f, 30f, 1000f)
  }

  {
    addActor(canvas)
    addListener(new DragListener {
      setButton(Input.Buttons.MIDDLE)

      override def touchDown(event: InputEvent, x: Float, y: Float, pointer: Int, button: Int): Boolean = {
        if (super.touchDown(event, x, y, pointer, button)) {
          Gdx.graphics.setSystemCursor(Cursor.SystemCursor.AllResize)
          return true
        }
        false
      }

      override def drag(event: InputEvent, x: Float, y: Float, pointer: Int): Unit = {
        xOffset += getDeltaX()
        yOffset += getDeltaY()
        updateCanvas()
      }

      override def touchUp(event: InputEvent, x: Float, y: Float, pointer: Int, button: Int): Unit = {
        super.touchUp(event, x, y, pointer, button)
        Gdx.graphics.setSystemCursor(Cursor.SystemCursor.Arrow)
      }
    })
  }

  def getCanvas(): Group = {
    canvas
  }

  override def hit(x: Float, y: Float, touchable: Boolean): Actor = {
    val hit = super.hit(x, y, touchable)
    if (hit != null) return hit
    if (touchable && (getTouchable() ne Touchable.enabled)) return null
    if (x >= 0 && x < getWidth() && y >= 0 && y < getHeight()) this else null
  }

  def getScale(): Float = {
    zoom * baseScale
  }

  def getZoom(): Float = {
    zoom
  }

  def setZoom(zoom: Float): Unit = {
    this.zoom = Math.max(minZoom, Math.min(maxZoom, zoom))
    updateCanvas()
  }

  def setBaseScale(baseScale: Float): Unit = {
    this.baseScale = baseScale
    updateCanvas()
  }

  def setPan(x: Float, y: Float): Unit = {
    xOffset = x
    yOffset = y
    updateCanvas()
  }

  def getXOffset(): Float = {
    xOffset
  }

  def getYOffset(): Float = {
    yOffset
  }

  /**
   * 以屏幕坐标 (stageX, stageY) 为锚点缩放。
   */
  def zoomAt(stageX: Float, stageY: Float, amountY: Float): Unit = {
    val zoomFactor = 1.1f
    val oldScale = getScale()
    setZoom((zoom * Math.pow(zoomFactor, -amountY)).toFloat)
    val newScale = getScale()
    if (newScale == oldScale) return

    screenPos.set(stageX, stageY)
    stageToLocalCoordinates(screenPos)
    xOffset = screenPos.x - (screenPos.x - xOffset) * (newScale / oldScale)
    yOffset = screenPos.y - (screenPos.y - yOffset) * (newScale / oldScale)
    updateCanvas()
  }

  def resetView(): Unit = {
    zoom = 1f
    xOffset = 0
    yOffset = 0
    updateCanvas()
  }

  private def updateCanvas(): Unit = {
    canvas.setPosition(xOffset, yOffset)
    canvas.setScale(getScale())
  }

  override def act(delta: Float): Unit = {
    val stage = getStage()
    if (stage != null && getParent() != null && (stage.getKeyboardFocus() eq getParent())) {
      val speed = moveSpeed * delta / getScale()
      if (App.shortcutManager.isActive(TlGroup.Actions.SCROLL_UP)) yOffset -= speed
      if (App.shortcutManager.isActive(TlGroup.Actions.SCROLL_DOWN)) yOffset += speed
      if (App.shortcutManager.isActive(TlGroup.Actions.SCROLL_LEFT)) xOffset += speed
      if (App.shortcutManager.isActive(TlGroup.Actions.SCROLL_RIGHT)) xOffset -= speed
    }
    updateCanvas()
    super.act(delta)
  }
}
