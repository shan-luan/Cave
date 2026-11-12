package com.lomekwi.cave.app.copy

import com.lomekwi.cave.project.{Id, IdSource, ProjectSession}
import com.lomekwi.cave.timeline.{Content, Instant, Interval, Segment, SegmentGroup, Timeline, Transition, Timespan}

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, ObjectInputStream, ObjectOutputStream}
import scala.collection.mutable
import scala.util.Using

/**
 * 剪贴板模板，一批「轨道 + 片段 + 区间 + origin」的快照，粘贴时按相对位置落回时间轴。
 */
class PasteTemplate private (val entries: mutable.ArrayBuffer[PasteTemplate.Entry], ids: IdSource) extends Copyable {


  /** 复制模板本身，片段再复制一份，位置与组结构沿用。 */
  override def copy(): Copyable = {
    new PasteTemplate(PasteTemplate.remap(entries, ids), ids)
  }

  /** 把模板按相对位置落回时间轴，返回实际放下的片段 id。 */
  def paste(context: ProjectSession, baseTrack: Int, baseTime: Instant): Vector[Id] = {
    if (entries.isEmpty) return Vector.empty
    val minTrack = entries.iterator.map(_.trackIndex).min
    val minStart = entries.iterator.map(_.interval.lo).min
    val delta = baseTime - minStart
    var timeline: Timeline = context.timeline
    val pasted = Vector.newBuilder[Id]
    for (entry <- entries) {
      val track = baseTrack + (entry.trackIndex - minTrack)
      val lo = entry.interval.lo + delta
      val interval = Interval(lo, lo + entry.interval.len)
      val before = timeline
      timeline = timeline.add(track, entry.segment, interval, entry.origin + delta)(using context.idSource)
      if (timeline ne before) {
        pasted += entry.segment.id
      }
    }
    for (group <- entries.iterator.flatMap(_.group).toVector.distinct) {
      timeline = timeline.adoptGroup(group)
    }
    context.applyTimeline(timeline)
    pasted.result()
  }
}

object PasteTemplate {
  case class Entry(trackIndex: Int, segment: Content, interval: Interval, origin: Instant, group: Option[SegmentGroup])

  // 复制时用 withId 换 id，Source 和 filter chain 共享。
  /** 把时间线上的一批片段抓成模板。转场不参与复制。 */
  def of(segments: Iterable[Segment], timeline: Timeline)(using ids: IdSource): PasteTemplate = {
    val raw = mutable.ArrayBuffer.empty[Entry]
    if (timeline == null) return new PasteTemplate(raw, ids)
    for (segment <- segments) {
      segment match {
        case _: Transition =>
        case c: Content =>
          timeline.findTrackOf(c.id).foreach { track =>
            raw += Entry(track.index, c, track.interval(c), track.getOrigin(c), timeline.getGroup(c.id))
          }
      }
    }
    new PasteTemplate(remap(raw, ids), ids)
  }

  /** 逐条复制，片段换新 Id，同一原组映射到同一个新组。 */
  private def remap(entries: mutable.ArrayBuffer[Entry], ids: IdSource): mutable.ArrayBuffer[Entry] = {
    val dups = mutable.ArrayBuffer.empty[Content]
    for (entry <- entries) {
      dups += copyForNewIdentity(entry.segment).withId(ids.nextId()).asInstanceOf[Content]
    }
    // 组是值，成员必须一次成型，否则先建的模板条目会停在半满的旧版本上
    val groupCopies: mutable.HashMap[SegmentGroup, SegmentGroup] = mutable.HashMap.empty
    for (i <- entries.indices) {
      entries(i).group.foreach { original =>
        groupCopies.put(original, groupCopies.getOrElse(original, SegmentGroup(Set.empty[Id])).add(dups(i).id))
      }
    }
    val copied = mutable.ArrayBuffer.empty[Entry]
    for (i <- entries.indices) {
      val entry = entries(i)
      val groupCopy = entry.group.map(original => groupCopies(original))
      copied += Entry(entry.trackIndex, dups(i), entry.interval, entry.origin, groupCopy)
    }
    copied
  }

  private def copyForNewIdentity(segment: Content): Content = {
    val bos = new ByteArrayOutputStream()
    Using.resource(new ObjectOutputStream(bos)) { oos =>
      oos.writeObject(segment)
    }
    val copy: Content = Using.resource(new ObjectInputStream(new ByteArrayInputStream(bos.toByteArray))) { ois =>
      ois.readObject().asInstanceOf[Content]
    }
    copy.source.onDuplicate(segment.source)
    copy
  }
}
