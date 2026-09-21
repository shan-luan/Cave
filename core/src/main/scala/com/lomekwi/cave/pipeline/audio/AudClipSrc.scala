package com.lomekwi.cave.pipeline.audio

import com.lomekwi.cave.app.AppAudioOut
import com.lomekwi.cave.pipeline.Node
import com.lomekwi.cave.pipeline.Source
import com.lomekwi.cave.pipeline.num.NumFrame
import com.lomekwi.cave.resource.media.AudRes
import com.lomekwi.cave.timeline.Track
import com.lomekwi.cave.ui.editpanel.tlarea.TlAudSrcActor
import com.lomekwi.cave.ui.editpanel.tlarea.TlSrcActor

@SerialVersionUID(1L)
class AudClipSrc(private var audRes: AudRes) extends Source[AudFrame] {
  addOutPort(new Node.OutPort[NumFrame]("时长", classOf[NumFrame]) {
    private final val `val`: NumFrame = new NumFrame(null)

    override def getData: NumFrame = {
      `val`.setVal(getDuration.toDouble)
      `val`
    }
  })

  def getAudRes: AudRes = {
    audRes
  }

  override def sync(time: Long, track: Track): Unit = {
    audRes.sync(track.index, time)
  }

  override protected def generate(time: Long, track: Track): AudFrame = {

    if (frame == null || (frame.track ne track)) {
      frame = new AudFrame(AppAudioOut.SAMPLE_RATE, track, this)
    }

    try {
      audRes.get(track.index, time, frame)
      if (frame.getSamples == null) null else frame
    } catch {
      case e: Exception =>
        throw new RuntimeException(e)
    }
  }

  override def getLengthPerExportFrame: Long = {
    audRes.getFrameLength
  }
  override def getDuration: Long = {
    audRes.getDuration
  }
  override def getFrameType: Class[AudFrame] = {
    classOf[AudFrame]
  }
  override def getDisplayName: String = {
    "音频源"
  }
  override def onDuplicate(original: Source[?]): Unit = {
    val src = original.asInstanceOf[AudClipSrc]
    this.audRes = src.audRes
  }
  override def createTlSrcActor(): TlSrcActor = {
    new TlAudSrcActor(this)
  }
}
