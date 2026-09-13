package com.lomekwi.cave.pipeline.image

import com.lomekwi.cave.util.Units.SECOND

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.lomekwi.cave.pipeline.Node
import com.lomekwi.cave.pipeline.Source
import com.lomekwi.cave.pipeline.num.NumFrame
import com.lomekwi.cave.resource.media.ImgRes
import com.lomekwi.cave.timeline.Segment
import com.lomekwi.cave.timeline.Track
import com.lomekwi.cave.ui.editpanel.previewarea.TransFrameActor
import com.lomekwi.cave.ui.editpanel.tlarea.ImgSegActor
import com.lomekwi.cave.ui.editpanel.tlarea.SegActor

import java.util.concurrent.CountDownLatch

@SerialVersionUID(1L)
class ImgSrc(private var imgRes: ImgRes) extends Source[ImgFrame] {
  @transient private var texture: Texture = null
  @transient private var actor: TransFrameActor = null
  @volatile @transient private var initialized: Boolean = false

  addOutPort(new Node.OutPort[NumFrame]("宽度", classOf[NumFrame]) {
    private final val `val`: NumFrame = new NumFrame(null)

    override def getData(): NumFrame = {
      `val`.setVal(imgRes.getWidth().toDouble)
      `val`
    }
  })
  addOutPort(new Node.OutPort[NumFrame]("高度", classOf[NumFrame]) {
    private final val `val`: NumFrame = new NumFrame(null)

    override def getData(): NumFrame = {
      `val`.setVal(imgRes.getHeight().toDouble)
      `val`
    }
  })
  addOutPort(new Node.OutPort[NumFrame]("时长", classOf[NumFrame]) {
    private final val `val`: NumFrame = new NumFrame(null)

    override def getData(): NumFrame = {
      `val`.setVal(getDuration().toDouble)
      `val`
    }
  })

  def getImgRes(): ImgRes = {
    imgRes
  }

  override def sync(time: Long, track: Track): Unit = {
    imgRes.sync(track.index, time)
  }

  override def generate(time: Long, track: Track): ImgFrame = {
    if (frame != null && (frame.track ne track)) {
      initialized = false
    }
    val cd = new CountDownLatch(1)
    if (!initialized) {
      Gdx.app.postRunnable(() => {
        if (texture == null) {
          texture = new Texture(imgRes.getWidth(), imgRes.getHeight(), Pixmap.Format.RGBA8888)
        }
        frame = new ImgFrame(track, this)
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
        case e: InterruptedException =>
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
    frame.getTransform().reset(0, 0)
    frame
  }

  override def getLengthPerExportFrame(): Long = {
    imgRes.getFrameLength()
  }

  override def getDuration(): Long = {
    Long.MaxValue
  }

  override def getDefaultSegmentDuration(): Long = {
    5 * SECOND
  }

  override def getFrameType(): Class[ImgFrame] = {
    classOf[ImgFrame]
  }

  override def getDisplayName(): String = {
    "\u56fe\u7247\u6e90"
  }

  override def onDuplicate(original: Source[?]): Unit = {
    val src = original.asInstanceOf[ImgSrc]
    this.imgRes = src.imgRes
  }

  override def createSegActor(segment: Segment): SegActor = {
    new ImgSegActor(segment)
  }
}
