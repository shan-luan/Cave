package com.lomekwi.cave.pipeline.image

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.lomekwi.cave.pipeline.{Source, Node, Segment, TransitionSource}
import com.lomekwi.cave.resource.media.VdoRes
import com.lomekwi.cave.timeline.Track
import com.lomekwi.cave.ui.editpanel.tlarea.{TlSegmentActor, TlVdoSegmentActor}

import java.util.concurrent.CountDownLatch

@SerialVersionUID(1L)
class VdoSource(var vdoRes: VdoRes) extends Source[ImgFrame] {
  @transient private lazy val texture: Texture = new Texture(vdoRes.width, vdoRes.height, Pixmap.Format.RGBA8888)
  @volatile @transient private var initialized: Boolean = false

  addOutPort(new Node.OutPort[Double]("宽度", classOf[Double]) {
    override def getData: Double = vdoRes.width.toDouble
  })
  addOutPort(new Node.OutPort[Double]("高度", classOf[Double]) {
    override def getData: Double = vdoRes.height.toDouble
  })
  addOutPort(new Node.OutPort[Double]("时长", classOf[Double]) {
    override def getData: Double = getDuration.toDouble
  })

  override def sync(time: Long, track: Track, segment: Segment): Unit = {
    vdoRes.sync(track.index, time)
  }

  override protected def produce(time: Long, track: Track, segment: Segment): ImgFrame = {
    if (frame != null && frame.trackIndex != track.index) {
      initialized = false
    }
    val cd = new CountDownLatch(1)
    if (!initialized) {
      Gdx.app.postRunnable(() => {
        frame = new ImgFrame(track.index, segment)
        frame.texture = texture
        frame.transform = new Transform(0, 0, 0)
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
      vdoRes.get(track.index, time, frame)
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
    vdoRes.frameLength
  }

  override def getDuration: Long = {
    vdoRes.duration
  }

  override def displayName: String = {
    "视频源"
  }

  override def createTlSegmentActor(segment: Segment): TlSegmentActor = {
    new TlVdoSegmentActor(segment)
  }

  override def canCreateTransitionWith(source: Source[?]): Boolean = {
    classOf[ImgFrame].isAssignableFrom(source.getType)
  }

  override def createTransition(source: Source[?]): TransitionSource[ImgFrame, BiRenderFrame] = {
    new CrossfadeSource(this, source.asInstanceOf[Source[ImgFrame]])
  }

  override def onDuplicate(original: Source[?]): Unit = {
    val source = original.asInstanceOf[VdoSource]
    this.vdoRes = source.vdoRes
  }
}
