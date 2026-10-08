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
  case class Entry(trackIndex: Int, segment: Content, range: Interval, origin: Long, group: SegmentGroup)

  /** 把时间线上的一批片段抓成模板。转场不参与复制。 */
  def of(segments: Iterable[Segment], timeline: Timeline): PasteTemplate = {
    val raw = mutable.ArrayBuffer.empty[Entry]
    if (timeline == null) return new PasteTemplate(raw)
    for (segment <- segments) {
      segment match {
        case _: Transition =>
        case c: Content =>
          val track = timeline.findTrackOf(c)
          if (track != null) {
            raw += Entry(track.index, c, track.getRange(c), track.getOrigin(c), timeline.getGroup(c))
          }
      }
    }
    new PasteTemplate(remap(raw))
  }

  /** 逐条深拷贝，片段复制一份，同一原组映射到同一个新组。 */
  private def remap(entries: mutable.ArrayBuffer[Entry]): mutable.ArrayBuffer[Entry] = {
    val copied = mutable.ArrayBuffer.empty[Entry]
    val groupCopies: mutable.HashMap[SegmentGroup, SegmentGroup] = mutable.HashMap.empty
    for (entry <- entries) {
      val dup = entry.segment.duplicate().asInstanceOf[Content]
      var groupCopy: SegmentGroup = null
      if (entry.group != null) {
        groupCopy = groupCopies.getOrElse(entry.group, null)
        if (groupCopy == null) {
          groupCopy = new SegmentGroup()
          groupCopies.put(entry.group, groupCopy)
        }
        groupCopy.add(dup)
      }
      copied += Entry(entry.trackIndex, dup, entry.range, entry.origin, groupCopy)
    }
    copied
  }
}
