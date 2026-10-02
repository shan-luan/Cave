package com.lomekwi.cave.pipeline.image

import com.lomekwi.cave.util.Units.SECOND

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.lomekwi.cave.pipeline.{Source, Node, Segment}
import com.lomekwi.cave.resource.media.ImgRes
import com.lomekwi.cave.timeline.Track
import com.lomekwi.cave.ui.editpanel.previewarea.TransFrameActor
import com.lomekwi.cave.ui.editpanel.tlarea.{TlImgSegmentActor, TlSegmentActor}

import java.util.concurrent.CountDownLatch

@SerialVersionUID(1L)
class ImgSource(var imgRes: ImgRes) extends Source[ImgFrame] {
  @transient private lazy val texture: Texture = new Texture(imgRes.width, imgRes.height, Pixmap.Format.RGBA8888)
  @transient private lazy val actor: TransFrameActor = new TransFrameActor(frame)
  @volatile @transient private var initialized: Boolean = false

  addOutPort(new Node.OutPort[Double]("宽度", classOf[Double]) {
    override def getData: Double = imgRes.width.toDouble
  })
  addOutPort(new Node.OutPort[Double]("高度", classOf[Double]) {
    override def getData: Double = imgRes.height.toDouble
  })
  addOutPort(new Node.OutPort[Double]("时长", classOf[Double]) {
    override def getData: Double = getDuration.toDouble
  })

  override def sync(time: Long, track: Track): Unit = {
    imgRes.sync(track.index, time)
  }

  override protected def produce(time: Long, track: Track, segment: Segment[ImgFrame]): ImgFrame = {
    if (frame != null && frame.trackIndex != track.index) {
      initialized = false
    }
    val cd = new CountDownLatch(1)
    if (!initialized) {
      Gdx.app.postRunnable(() => {
        frame = new ImgFrame(track.index, segment)
        frame.texture = texture
        frame.transform = new Transform(0, 0, 0)
        actor.rebind(frame)
        frame.actor = actor
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
    frame.transform.reset(0, 0)
    frame.opacity = 1f
    frame
  }

  override def getLengthPerExportFrame: Long = {
    imgRes.frameLength
  }

  override def getDuration: Long = {
    Long.MaxValue
  }

  override def getDefaultDuration: Long = {
    5 * SECOND
  }

  override def displayName: String = {
    "图片源"
  }

  override def createTlSegmentActor(segment: Segment[?]): TlSegmentActor = {
    new TlImgSegmentActor(segment)
  }

  override def onDuplicate(original: Source[?]): Unit = {
    val source = original.asInstanceOf[ImgSource]
    this.imgRes = source.imgRes
  }
}
