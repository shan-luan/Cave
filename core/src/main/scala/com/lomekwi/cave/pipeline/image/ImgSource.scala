package com.lomekwi.cave.pipeline.image

import com.lomekwi.cave.util.i18n.*
import com.lomekwi.cave.util.i18n.BasicLang.*

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.lomekwi.cave.pipeline.{Source, Node, TransitionSource, FilterChain, NodeGraph}
import com.lomekwi.cave.resource.media.ImgRes
import com.lomekwi.cave.timeline.{Segment, Timespan, Track}
import com.lomekwi.cave.ui.editpanel.tlarea.{TlImgSegmentActor, TlSegmentActor}

import java.util.concurrent.CountDownLatch

@SerialVersionUID(1L)
class ImgSource(var imgRes: ImgRes) extends Source[ImgFrame] {
  @transient private lazy val texture: Texture = new Texture(imgRes.width, imgRes.height, Pixmap.Format.RGBA8888)
  @volatile @transient private var initialized: Boolean = false

  addOutPort(new Node.OutPort[Double](("宽度".in(Zh) | "Width".in(En)).str(), classOf[Double]) {
    override def getData(context: FilterChain | NodeGraph): Double = imgRes.width.toDouble
  })
  addOutPort(new Node.OutPort[Double](("高度".in(Zh) | "Height".in(En)).str(), classOf[Double]) {
    override def getData(context: FilterChain | NodeGraph): Double = imgRes.height.toDouble
  })

  override def sync(time: Timespan, track: Track, segment: Segment): Unit = {
    imgRes.sync(segment, time)
  }

  override protected def produce(time: Timespan, track: Track, segment: Segment): ImgFrame = {
    if (frame != null && frame.trackIndex != track.index) {
      initialized = false
    }
    val cd = new CountDownLatch(1)
    if (!initialized) {
      Gdx.app.postRunnable(() => {
        frame = new ImgFrame(track.index, segment.id)
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
      imgRes.get(segment, time, frame)
    } catch {
      case e: Exception =>
        throw new RuntimeException(e)
    }
    frame.transform.reset(0, 0)
    frame.opacity = 1f
    frame
  }

  override def getLengthPerExportFrame: Timespan = {
    imgRes.frameLength
  }

  override def getDuration: Timespan = {
    Timespan.MaxValue
  }

  override def getDefaultDuration: Option[Timespan] = {
    Some(Timespan.SECOND * 5)
  }

  override def displayName: String = {
    ("图片源".in(Zh) | "Image Source".in(En)).str()
  }

  override def createTlSegmentActor(segment: Segment): TlSegmentActor = {
    new TlImgSegmentActor(segment)
  }

  override def canCreateTransitionWith(source: Source[?]): Boolean = {
    classOf[ImgFrame].isAssignableFrom(source.getType)
  }

  override def createTransition(source: Source[?]): TransitionSource[ImgFrame, BiRenderFrame] = {
    new CrossfadeSource(this, source.asInstanceOf[Source[ImgFrame]])
  }

  override def onDuplicate(original: Source[?]): Unit = {
    val source = original.asInstanceOf[ImgSource]
    this.imgRes = source.imgRes
  }
}
