package com.lomekwi.cave.ui.editpanel.previewarea

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.google.common.eventbus.Subscribe
import com.lomekwi.cave.pipeline.Frame
import com.lomekwi.cave.pipeline.GapFrame
import com.lomekwi.cave.project.Project
import com.lomekwi.cave.pipeline.image.ImgFrame
import com.lomekwi.cave.pipeline.text.TextFrame
import com.lomekwi.cave.timeline.Segment
import com.lomekwi.cave.timeline.SegmentSelectedEvent
import com.lomekwi.cave.timeline.Track
import com.lomekwi.cave.app.App
import com.lomekwi.cave.task.ExportOptions
import com.lomekwi.cave.task.ExportOptionsSet
import com.lomekwi.cave.task.ExportPresetsChangedEvent
import com.lomekwi.cave.ui.Colors
import com.lomekwi.cave.ui.Focusable
import com.lomekwi.cave.ui.widget.PanZoomCanvas
import com.lomekwi.cave.util.Units

import java.util.ArrayList
import java.util.List

import scala.jdk.CollectionConverters.*

/**
 * 负责渲染和显示预览内容
 */
class PreviewArea(project0: Project) extends Group with Focusable {

  private final val project: Project = project0
  private final val panZoom: PanZoomCanvas = new PanZoomCanvas(0.07f, 30f, 1000f)
  private final val canvas: Group = panZoom.getCanvas()
  //此列表仅应在主线程读取.
  private final val frames: List[Frame] = new ArrayList[Frame]()
  private var refViewportArea: Float = -1f
  private var lastWidth: Float = 0
  private var exportOpts: ExportOptionsSet = null
  private var lastHeight: Float = 0

  project.projEventBus.register(this)
  App.appEventBus.register(this)
  addActor(panZoom)
  setupDragListener()

  private def recalcScale(): Unit = {
    val vr: Float = if (refViewportArea > 0) Math.sqrt(getWidth().toDouble * getHeight() / refViewportArea).toFloat else 1f
    panZoom.setBaseScale(vr)
  }

  private def setupDragListener(): Unit = {
    addListener(new ClickListener {
      override def touchDown(event: InputEvent, x: Float, y: Float, pointer: Int, button: Int): Boolean = {
        if (x < 0 || x > getWidth() || y < 0 || y > getHeight()) return false
        super.touchDown(event, x, y, pointer, button)
      }

      override def clicked(event: InputEvent, x: Float, y: Float): Unit = {
        if (event.getTarget().isInstanceOf[TransFrameActor]) return
        val editPanel = App.root.getFrontendEditPanel()
        if (editPanel != null) {
          val tlGroup = editPanel.getTlGroup()
          tlGroup.clearSelection()
        }
      }

      override def scrolled(event: InputEvent, x: Float, y: Float, amountX: Float, amountY: Float): Boolean = {
        panZoom.zoomAt(event.getStageX(), event.getStageY(), amountY)
        true
      }
    })

  }

  @Subscribe
  def sink(frame: ImgFrame): Unit = {
    val track: Track = frame.track
    track.getWorker().getSinkPhaser().register()
    Gdx.app.postRunnable(() => {
      setFrame(frame)
      frame.upload()
      val i: TransFrameActor = frame.getActor()
      canvas.addActor(i)
      track.getWorker().getSinkPhaser().arriveAndDeregister()
    })
  }

  @Subscribe
  def sink(frame: TextFrame): Unit = {
    val track: Track = frame.track
    track.getWorker().getSinkPhaser().register()
    Gdx.app.postRunnable(() => {
      setFrame(frame)
      val i: TransFrameActor = frame.getActor()
      canvas.addActor(i)
      track.getWorker().getSinkPhaser().arriveAndDeregister()
    })
  }

  private def setFrame(frame: Frame): Unit = {
    var idx: Int = frame.track.index
    while (idx >= frames.size()) {
      frames.add(null)
    }
    val legacy = frames.set(frame.track.index, frame)
    if (legacy != null) {
      val actor = PreviewArea.getFrameActor(legacy)
      if (actor != null) canvas.removeActor(actor)
    }
  }

  //TODO:减少对象分配开销
  def clearFrames(idx: Int): Unit = {
    Gdx.app.postRunnable(new Runnable {
      override def run(): Unit = {
        // 边界检查
        if (idx < 0 || idx >= frames.size()) {
          return
        }
        val frame = frames.get(idx)
        if (frame == null) {
          return
        }
        val actor = PreviewArea.getFrameActor(frame)
        if (actor == null) {
          return
        }
        // 所有条件满足，执行清理
        frames.set(idx, null)
        canvas.removeActor(actor)
      }
    })
  }

  @Subscribe
  def clear(event: GapFrame): Unit = {
    clearFrames(event.track.index)
  }

  @Subscribe
  def onSegmentSelected(event: SegmentSelectedEvent): Unit = {
    for (frame <- frames.asScala) {
      if (frame != null) {
        val actor = PreviewArea.getFrameActor(frame)
        if (actor != null) {
          val segment: Segment = if (frame.getSource() != null) frame.getSource().getSegment() else null
          val selected = segment != null && segment.isSelected()
          actor.setSelected(selected)
        }
      }
    }
  }

  override def act(delta: Float): Unit = {
    super.act(delta)
    canvas.setZIndex(0)
    var i = 0
    for (frame <- frames.asScala) {
      if (!(frame == null || frame.isClosed())) {
        val actor = PreviewArea.getFrameActor(frame)
        if (actor != null && (actor.getParent() eq canvas)) {
          actor.setZIndex(i)
          i += 1
        }
      }
    }
  }

  override def draw(batch: Batch, parentAlpha: Float): Unit = {
    App.root.getShapeDrawer().filledRectangle(getX(), getY(), getWidth(), getHeight(), Color.BLACK)
    drawAxes()
    drawPresetOutlines()
    super.draw(batch, parentAlpha)
    drawSnapGuides()
  }

  private def drawSnapGuides(): Unit = {
    val drawer = App.root.getShapeDrawer()
    val it = canvas.getChildren().iterator()
    while (it.hasNext) {
      val child: Actor = it.next()
      if (child.isInstanceOf[TransFrameActor]) {
        val tfa = child.asInstanceOf[TransFrameActor]
        val lx = tfa.getSnapLineX()
        val ly = tfa.getSnapLineY()
        if (!(java.lang.Float.isNaN(lx) && java.lang.Float.isNaN(ly))) {
          if (!java.lang.Float.isNaN(lx)) {
            PreviewArea.guidePos.set(lx, 0f)
            canvas.localToParentCoordinates(PreviewArea.guidePos)
            drawer.line(PreviewArea.guidePos.x, getY(), PreviewArea.guidePos.x, getY() + getHeight(), Colors.SNAP_GUIDE, 2f)
          }
          if (!java.lang.Float.isNaN(ly)) {
            PreviewArea.guidePos.set(0f, ly)
            canvas.localToParentCoordinates(PreviewArea.guidePos)
            drawer.line(getX(), PreviewArea.guidePos.y, getX() + getWidth(), PreviewArea.guidePos.y, Colors.SNAP_GUIDE, 2f)
          }
        }
      }
    }
  }

  @Subscribe
  def onExportPresetsChanged(e: ExportPresetsChangedEvent): Unit = {
    exportOpts = null
  }

  private def drawPresetOutlines(): Unit = {
    if (exportOpts == null) exportOpts = ExportOptionsSet.load()
    val drawer = App.root.getShapeDrawer()
    val ox = getX() + canvas.getX()
    val oy = getY() + canvas.getY()
    val s = canvas.getScaleX()
    for (opts <- exportOpts.presets.asScala) {
      if (!(opts.width <= 0 || opts.height <= 0)) {
        drawer.rectangle(ox, oy, opts.width * s, opts.height * s, Colors.PREVIEW_GUIDE, 1f)
      }
    }
  }

  private def drawAxes(): Unit = {
    val drawer = App.root.getShapeDrawer()
    val ox = getX() + canvas.getX()
    val oy = getY() + canvas.getY()
    val x0 = getX()
    val x1 = getX() + getWidth()
    val y0 = getY()
    val y1 = getY() + getHeight()

    drawer.line(x0, oy, x1, oy, Colors.PREVIEW_GUIDE)
    drawer.line(ox, y0, ox, y1, Colors.PREVIEW_GUIDE)

    val tickHalf = 4f
    val interval = Units.niceInterval(PreviewArea.TICK_PIXEL_TARGET / canvas.getScaleX())

    var startV = (x0 - ox) / canvas.getScaleX()
    var endV = (x1 - ox) / canvas.getScaleX()
    var first = Math.ceil((startV / interval).toDouble) * interval
    var v = first
    while (v <= endV) {
      if (!(Math.abs(v) < interval * 0.01f)) {
        val sx = ox + v.toFloat * canvas.getScaleX()
        drawer.line(sx, oy - tickHalf, sx, oy + tickHalf, Colors.PREVIEW_GUIDE)
      }
      v += interval
    }

    startV = (y0 - oy) / canvas.getScaleX()
    endV = (y1 - oy) / canvas.getScaleX()
    first = Math.ceil((startV / interval).toDouble) * interval
    v = first
    while (v <= endV) {
      if (!(Math.abs(v) < interval * 0.01f)) {
        val sy = oy + v.toFloat * canvas.getScaleX()
        drawer.line(ox - tickHalf, sy, ox + tickHalf, sy, Colors.PREVIEW_GUIDE)
      }
      v += interval
    }
  }

  override def sizeChanged(): Unit = {
    panZoom.setSize(getWidth(), getHeight())
    if (lastWidth > 0 && lastHeight > 0) {
      if (refViewportArea < 0) {
        refViewportArea = lastWidth * lastHeight
      }
      recalcScale()
    }
    lastWidth = getWidth()
    lastHeight = getHeight()
  }

  def resetView(): Unit = {
    panZoom.resetView()
  }

  def dispose(): Unit = {
    project.projEventBus.unregister(this)
    App.appEventBus.unregister(this)
  }
}

object PreviewArea {
  private final val TICK_PIXEL_TARGET: Int = 80
  private final val guidePos: Vector2 = new Vector2()

  private def getFrameActor(frame: Frame): TransFrameActor = {
    frame match {
      case imgFrame: ImgFrame => imgFrame.getActor()
      case textFrame: TextFrame => textFrame.getActor()
      case _ => null
    }
  }
}
