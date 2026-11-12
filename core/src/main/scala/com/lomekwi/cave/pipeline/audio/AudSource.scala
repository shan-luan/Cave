package com.lomekwi.cave.pipeline.audio

import com.lomekwi.cave.app.AppAudioOut
import com.lomekwi.cave.pipeline.{Source, Node, TransitionSource}
import com.lomekwi.cave.resource.media.AudRes
import com.lomekwi.cave.timeline.{Segment, Timespan, Track}
import com.lomekwi.cave.ui.editpanel.tlarea.{TlAudSegmentActor, TlSegmentActor}
import com.lomekwi.cave.util.i18n.*
import com.lomekwi.cave.util.i18n.BasicLang.*

@SerialVersionUID(1L)
class AudSource(var audRes: AudRes) extends Source[AudFrame] {
  addOutPort(new Node.OutPort[Double](("时长".in(Zh) | "Duration".in(En)).str(), classOf[Double]) {
    override def getData: Double = getDuration.value
  })

  override def sync(time: Timespan, track: Track, segment: Segment): Unit = {
    audRes.sync(segment, time)
  }

  override protected def produce(time: Timespan, track: Track, segment: Segment): AudFrame = {
    if (frame == null || frame.trackIndex != track.index) {
      frame = new AudFrame(AppAudioOut.SAMPLE_RATE, track.index, segment.id)
    }

    try {
      audRes.get(segment, time, frame)
      if (frame.samples == null) null else frame
    } catch {
      case e: Exception =>
        throw new RuntimeException(e)
    }
  }

  override def getLengthPerExportFrame: Timespan = {
    audRes.frameLength
  }

  override def getDuration: Timespan = {
    audRes.duration
  }

  override def getDefaultDuration: Option[Timespan] = {
    Some(audRes.duration)
  }

  override def displayName: String = {
    ("音频源".in(Zh) | "Audio Source".in(En)).str()
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
