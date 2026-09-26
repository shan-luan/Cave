package com.lomekwi.cave.app.copy

import com.lomekwi.cave.pipeline.Segment
import com.lomekwi.cave.timeline.{Interval, SegmentGroup, Timeline}

import java.util

import scala.jdk.CollectionConverters.*

/**
 * 剪贴板模板，一批「轨道 + 片段 + 区间 + origin」的快照，粘贴时按相对位置落回时间轴。
 * 与选中集分开，选中集描述的是当前时间线里的对象，而模板要能在时间轴任意位置重放，
 * 因此位置信息必须随模板一起携带，不能指望从时间线反查。
 * 轨道不可变，模板可能等到原轨道换过多次版本后才被粘贴，故这里记索引。
 */
class PasteTemplate private (private val entries: util.List[PasteTemplate.Entry]) extends Copyable {

  def getEntries: util.List[PasteTemplate.Entry] = entries

  /** 复制模板本身，片段再深拷贝一份，位置与组结构沿用。 */
  override def copy(): Copyable = {
    new PasteTemplate(PasteTemplate.remap(entries))
  }
}

object PasteTemplate {
  case class Entry(trackIndex: Int, segment: Segment[?], range: Interval, origin: Long, group: SegmentGroup)

  /** 把时间线上的一批片段抓成模板。 */
  def of(segments: util.Collection[Segment[?]], timeline: Timeline): PasteTemplate = {
    val raw = new util.ArrayList[Entry](segments.size())
    if (timeline == null) return new PasteTemplate(raw)
    for (segment <- segments.asScala) {
      val track = timeline.findTrackOf(segment)
      if (track != null) {
        raw.add(Entry(track.index, segment, track.getRange(segment), track.getOrigin(segment), timeline.getGroup(segment)))
      }
    }
    new PasteTemplate(remap(raw))
  }

  /** 逐条深拷贝，片段复制一份，同一原组映射到同一个新组。 */
  private def remap(entries: util.List[Entry]): util.List[Entry] = {
    val copied = new util.ArrayList[Entry](entries.size())
    val groupCopies: util.Map[SegmentGroup, SegmentGroup] = new util.HashMap[SegmentGroup, SegmentGroup]()
    for (entry <- entries.asScala) {
      val dup = entry.segment.duplicate()
      var groupCopy: SegmentGroup = null
      if (entry.group != null) {
        groupCopy = groupCopies.get(entry.group)
        if (groupCopy == null) {
          groupCopy = new SegmentGroup()
          groupCopies.put(entry.group, groupCopy)
        }
        groupCopy.add(dup)
      }
      copied.add(Entry(entry.trackIndex, dup, entry.range, entry.origin, groupCopy))
    }
    copied
  }
}
