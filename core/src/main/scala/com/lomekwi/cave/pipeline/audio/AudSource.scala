package com.lomekwi.cave.pipeline.audio

import com.lomekwi.cave.app.AppAudioOut
import com.lomekwi.cave.pipeline.{Source, Node, Segment, TransitionSource}
import com.lomekwi.cave.resource.media.AudRes
import com.lomekwi.cave.timeline.Track
import com.lomekwi.cave.ui.editpanel.tlarea.{TlAudSegmentActor, TlSegmentActor}

@SerialVersionUID(1L)
class AudSource(var audRes: AudRes) extends Source[AudFrame] {
  addOutPort(new Node.OutPort[Double]("时长", classOf[Double]) {
    override def getData: Double = getDuration.toDouble
  })

  override def sync(time: Long, track: Track, segment: Segment): Unit = {
    audRes.sync(segment, time)
  }

  override protected def produce(time: Long, track: Track, segment: Segment): AudFrame = {
    // 轨道按索引唯一，帧携带的轨道只要索引相同就仍然对应当前的轨道线程，可以接着用
    if (frame == null || frame.trackIndex != track.index) {
      frame = new AudFrame(AppAudioOut.SAMPLE_RATE, track.index, segment)
    }

    try {
      audRes.get(segment, time, frame)
      if (frame.samples == null) null else frame
    } catch {
      case e: Exception =>
        throw new RuntimeException(e)
    }
  }

  override def getLengthPerExportFrame: Long = {
    audRes.frameLength
  }

  override def getDuration: Long = {
    audRes.duration
  }

  override def getDefaultDuration: Option[Long] = {
    Some(audRes.duration)
  }

  override def displayName: String = {
    "音频源"
  }

  override def createTlSegmentActor(segment: Segment): TlSegmentActor = {
    new TlAudSegmentActor(segment)
  }

  override def canCreateTransitionWith(source: Source[?]): Boolean = {
    classOf[AudFrame].isAssignableFrom(source.getType)
  }

  override def createTransition(source: Source[?]): TransitionSource[AudFrame, AudFrame] = {
    new AudCrossfadeSource(this, source.asInstanceOf[Source[AudFrame]])
  }

  override def onDuplicate(original: Source[?]): Unit = {
    val source = original.asInstanceOf[AudSource]
    this.audRes = source.audRes
  }
}
