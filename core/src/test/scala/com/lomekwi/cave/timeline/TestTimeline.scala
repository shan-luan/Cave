package com.lomekwi.cave.timeline

import com.lomekwi.cave.project.{Project, ProjectSession}

import scala.collection.Set

final class TestTimeline(val context: ProjectSession) {
  def timeline: Timeline = context.timeline

  def project: Project = context.project

  def tryAdd(track: Track, segment: Content, interval: Interval, origin: Long): Long =
    context.tryAdd(track, segment, interval, origin)

  def addOrThrow(track: Track, segment: Content, interval: Interval, origin: Long): Unit =
    context.applyTimeline(timeline.addOrThrow(track, segment, interval, origin))

  def remove(segment: Segment): Unit = context.remove(segment)

  def remove(segments: Iterable[Segment]): Unit = context.remove(segments)

  def split(track: Track, time: Long): Unit = context.split(track, time)

  def setStart(segments: Iterable[Segment], deltaTime: Long): Long = context.setStart(segments, deltaTime)

  def setEnd(segments: Iterable[Segment], deltaTime: Long): Long = context.setEnd(segments, deltaTime)

  def moveTime(segments: Iterable[Segment], deltaTime: Long): Long = context.moveTime(segments, deltaTime)

  def moveTrack(segments: Iterable[Segment], deltaTrack: Int): Int = context.moveTrack(segments, deltaTrack)

  def newGroup(): SegmentGroup = {
    val (next, group) = timeline.newGroup()
    context.applyTimeline(next)
    group
  }

  def adoptGroup(group: SegmentGroup): Unit = context.applyTimeline(timeline.adoptGroup(group))

  def groupAdd(group: SegmentGroup, segment: Segment): SegmentGroup = {
    val next = group.add(segment)
    context.applyTimeline(timeline.replaceGroup(group, next))
    next
  }

  def dropGroup(group: SegmentGroup): Unit = context.applyTimeline(timeline.dropGroup(group))

  def record(): ProjectSession.Recording = context.record()

  def snapTime(time: Long, threshold: Long, ignore: Set[Segment]): Long = timeline.snapTime(time, threshold, ignore)

  def findTrackOf(segment: Segment): Option[Track] = timeline.findTrackOf(segment)

  def getGroup(segment: Segment): Option[SegmentGroup] = timeline.getGroup(segment)

  def getTrackOrCreate(index: Int): Track = timeline.getTrackOrCreate(index)

  def getTrackCount: Int = timeline.getTrackCount

  def getTracks: Seq[Track] = timeline.getTracks

  def getLength: Long = timeline.getLength
}
