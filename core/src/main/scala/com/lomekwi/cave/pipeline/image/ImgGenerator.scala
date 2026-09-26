package com.lomekwi.cave.pipeline.image

import com.lomekwi.cave.util.Units.SECOND

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.lomekwi.cave.pipeline.{Generator, Node, Source}
import com.lomekwi.cave.resource.media.ImgRes
import com.lomekwi.cave.timeline.Track
import com.lomekwi.cave.ui.editpanel.previewarea.TransFrameActor
import com.lomekwi.cave.ui.editpanel.tlarea.{TlImgSrcActor, TlSrcActor}

import java.util.concurrent.CountDownLatch
import scala.compiletime.uninitialized

@SerialVersionUID(1L)
class ImgGenerator(private var imgRes: ImgRes) extends Generator[ImgFrame] {
  @transient private var texture: Texture = uninitialized
  @transient private var actor: TransFrameActor = uninitialized
  @volatile @transient private var initialized: Boolean = false

  addOutPort(new Node.OutPort[Double]("宽度", classOf[Double]) {
    override def getData: Double = imgRes.getWidth.toDouble
  })
  addOutPort(new Node.OutPort[Double]("高度", classOf[Double]) {
    override def getData: Double = imgRes.getHeight.toDouble
  })
  addOutPort(new Node.OutPort[Double]("时长", classOf[Double]) {
    override def getData: Double = getDuration.toDouble
  })

  def getImgRes: ImgRes = {
    imgRes
  }

  override def sync(time: Long, track: Track): Unit = {
    imgRes.sync(track.index, time)
  }

  override protected def produce(time: Long, track: Track, source: Source[ImgFrame]): ImgFrame = {
    if (frame != null && frame.trackIndex != track.index) {
      initialized = false
    }
    val cd = new CountDownLatch(1)
    if (!initialized) {
      Gdx.app.postRunnable(() => {
        if (texture == null) {
          texture = new Texture(imgRes.getWidth, imgRes.getHeight, Pixmap.Format.RGBA8888)
        }
        frame = new ImgFrame(track.index, source)
        frame.setTexture(texture)
          .setTransform(new Transform(0, 0, 0))
        if (actor == null) {
          actor = new TransFrameActor(frame)
        } else {
          actor.rebind(frame)
        }
        frame.setActor(actor)
        initialized = true
        cd.countDown()
      })
      try {
        cd.await()
      } catch {
        case _: InterruptedException =>
          Thread.currentThread().interrupt()
          return null
      }
    }
    try {
      imgRes.get(track.index, time, frame)
    } catch {
      case e: Exception =>
        e.printStackTrace()
        frame.setPixels(null)
    }
    frame.getTransform.reset(0, 0)
    frame
  }

  override def getLengthPerExportFrame: Long = {
    imgRes.getFrameLength
  }

  override def getDuration: Long = {
    Long.MaxValue
  }

  override def getDefaultDuration: Long = {
    5 * SECOND
  }

  override def getDisplayName: String = {
    "图片源"
  }

  override def createTlSrcActor(source: Source[?]): TlSrcActor = {
    new TlImgSrcActor(source)
  }

  override def onDuplicate(original: Generator[?]): Unit = {
    val src = original.asInstanceOf[ImgGenerator]
    this.imgRes = src.imgRes
  }
}
