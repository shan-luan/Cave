package com.lomekwi.cave.app.copy

import com.lomekwi.cave.pipeline.{Content, Segment, Transition}
import com.lomekwi.cave.timeline.{Interval, SegmentGroup, Timeline}

import scala.collection.mutable

/**
 * 剪贴板模板，一批「轨道 + 片段 + 区间 + origin」的快照，粘贴时按相对位置落回时间轴。
 */
class PasteTemplate private (val entries: mutable.ArrayBuffer[PasteTemplate.Entry]) extends Copyable {

  // 与选中集分开，选中集描述的是当前时间线里的对象，而模板要能在时间轴任意位置重放，
  // 因此位置信息必须随模板一起携带，不能指望从时间线反查。
  // 轨道不可变，模板可能等到原轨道换过多次版本后才被粘贴，故这里记索引。

  /** 复制模板本身，片段再深拷贝一份，位置与组结构沿用。 */
  override def copy(): Copyable = {
    new PasteTemplate(PasteTemplate.remap(entries))
  }
}

object PasteTemplate {
  case class Entry(trackIndex: Int, segment: Content, range: Interval, origin: Long, group: Option[SegmentGroup])

  /** 把时间线上的一批片段抓成模板。转场不参与复制。 */
  def of(segments: Iterable[Segment], timeline: Timeline): PasteTemplate = {
    val raw = mutable.ArrayBuffer.empty[Entry]
    if (timeline == null) return new PasteTemplate(raw)
    for (segment <- segments) {
      segment match {
        case _: Transition =>
        case c: Content =>
          timeline.findTrackOf(c).foreach { track =>
            raw += Entry(track.index, c, track.getRange(c), track.getOrigin(c), timeline.getGroup(c))
          }
      }
    }
    new PasteTemplate(remap(raw))
  }

  /** 逐条深拷贝，片段复制一份，同一原组映射到同一个新组。 */
  private def remap(entries: mutable.ArrayBuffer[Entry]): mutable.ArrayBuffer[Entry] = {
    val dups = mutable.ArrayBuffer.empty[Content]
    for (entry <- entries) {
      dups += entry.segment.duplicate().asInstanceOf[Content]
    }
    // 组是值，成员必须一次成型，否则先建的模板条目会停在半满的旧版本上
    val groupCopies: mutable.HashMap[SegmentGroup, SegmentGroup] = mutable.HashMap.empty
    for (i <- entries.indices) {
      entries(i).group.foreach { original =>
        groupCopies.put(original, groupCopies.getOrElse(original, new SegmentGroup(Set.empty)).add(dups(i)))
      }
    }
    val copied = mutable.ArrayBuffer.empty[Entry]
    for (i <- entries.indices) {
      val entry = entries(i)
      val groupCopy = entry.group.map(original => groupCopies(original))
      copied += Entry(entry.trackIndex, dups(i), entry.range, entry.origin, groupCopy)
    }
    copied
  }
}
