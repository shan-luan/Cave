package com.lomekwi.cave.pipeline.audio

import com.lomekwi.cave.app.AppAudioOut
import com.lomekwi.cave.pipeline.{Source, Node, Segment}
import com.lomekwi.cave.resource.media.AudRes
import com.lomekwi.cave.timeline.Track
import com.lomekwi.cave.ui.editpanel.tlarea.{TlAudSegmentActor, TlSegmentActor}

@SerialVersionUID(1L)
class AudSource(private var audRes: AudRes) extends Source[AudFrame] {
  addOutPort(new Node.OutPort[Double]("时长", classOf[Double]) {
    override def getData: Double = getDuration.toDouble
  })

  def getAudRes: AudRes = {
    audRes
  }

  override def sync(time: Long, track: Track): Unit = {
    audRes.sync(track.index, time)
  }

  override protected def produce(time: Long, track: Track, segment: Segment[AudFrame]): AudFrame = {
    // 轨道按索引唯一，帧携带的轨道只要索引相同就仍然对应当前的轨迹线程，可以接着用
    if (frame == null || frame.trackIndex != track.index) {
      frame = new AudFrame(AppAudioOut.SAMPLE_RATE, track.index, segment)
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

  override def getDisplayName: String = {
    "音频源"
  }

  override def createTlSegmentActor(segment: Segment[?]): TlSegmentActor = {
    new TlAudSegmentActor(segment)
  }

  override def onDuplicate(original: Source[?]): Unit = {
    val source = original.asInstanceOf[AudSource]
    this.audRes = source.audRes
  }
}
