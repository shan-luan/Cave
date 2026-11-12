package com.lomekwi.cave.timeline

import com.lomekwi.cave.project.{IdSource, Project, ProjectSession}

import scala.collection.Set

final class TestTimeline(val session: ProjectSession) {
  given idSource: IdSource = session.idSource

  def timeline: Timeline = session.timeline

  def project: Project = session.project

  def add(track: Track, segment: Content, interval: Interval, origin: Instant): Boolean = {
    val before = timeline
    val next = before.add(track.index, segment, interval, origin)
    session.applyProject(_.withTimeline(next))
    timeline ne before
  }

  def remove(segment: Segment): Unit = session.applyProject(p => p.withTimeline(p.timeline.remove(segment.id)))

  def remove(segments: Iterable[Segment]): Unit = session.applyProject(p => p.withTimeline(p.timeline.remove(segments.map(_.id))))

  def split(track: Track, time: Instant): Unit = session.applyProject(p => p.withTimeline(p.timeline.split(track, time)(using idSource)))

  def setStart(segments: Iterable[Segment], deltaTime: Timespan): Timespan = {
    val (next, applied) = timeline.setStart(segments.map(_.id), deltaTime)
    session.applyProject(_.withTimeline(next))
    applied
  }

  def setEnd(segments: Iterable[Segment], deltaTime: Timespan): Timespan = {
    val (next, applied) = timeline.setEnd(segments.map(_.id), deltaTime)
    session.applyProject(_.withTimeline(next))
    applied
  }

  def moveTime(segments: Iterable[Segment], deltaTime: Timespan): Timespan = {
    val (next, applied) = timeline.moveTime(segments.map(_.id), deltaTime)
    session.applyProject(_.withTimeline(next))
    applied
  }

  def moveTrack(segments: Iterable[Segment], deltaTrack: Int): Int = {
    val (next, applied) = timeline.moveTrack(segments.map(_.id), deltaTrack)
    session.applyProject(_.withTimeline(next))
    applied
  }

  def newGroup(): SegmentGroup = {
    val (next, group) = timeline.newGroup()
    session.applyProject(_.withTimeline(next))
    group
  }

  def adoptGroup(group: SegmentGroup): Unit = session.applyProject(_.withTimeline(timeline.adoptGroup(group)))

  def groupAdd(group: SegmentGroup, segment: Segment): SegmentGroup = {
    val next = group.add(segment.id)
    session.applyProject(_.withTimeline(timeline.replaceGroup(group, next)))
    next
  }

  def dropGroup(group: SegmentGroup): Unit = session.applyProject(_.withTimeline(timeline.dropGroup(group)))

  def snapshot(): Unit = session.snapshot()

  def snapTime(time: Instant, threshold: Timespan, ignore: Set[Segment]): Instant = timeline.snapTime(time, threshold, ignore.map(_.id))

  def findTrackOf(segment: Segment): Option[Track] = timeline.findTrackOf(segment.id)

  def getGroup(segment: Segment): Option[SegmentGroup] = timeline.getGroup(segment.id)

  def getTrackOrCreate(index: Int): Track = timeline.getTrackOrCreate(index)

  def getTrackCount: Int = timeline.getTrackCount

  def getTracks: Seq[Track] = timeline.getTracks

  def getLength: Instant = timeline.getLength
}
