package com.lomekwi.cave.timeline

import java.io.Serializable

import com.lomekwi.cave.timeline.Timespan.*
import com.lomekwi.cave.util.i18n.*
import com.lomekwi.cave.util.i18n.BasicLang.*

/** 左闭右开区间 [lo, hi)。 */
@SerialVersionUID(1L)
case class Interval(lo: Instant, hi: Instant) extends Serializable {
  require(lo <= hi, s"${("区间下界不得大于上界: ".in(Zh) | "The lower bound of an interval must not exceed its upper bound: ".in(En)).str()}[$lo, $hi)")

  def isEmpty: Boolean = lo == hi

  def len: Timespan = hi - lo

  def mid: Instant = lo + len / 2

  def contains(time: Instant): Boolean = lo <= time && time < hi

  def intersects(other: Interval): Boolean = lo < other.hi && other.lo < hi

  /** 与另一区间的交集。 */
  def &(other: Interval): Option[Interval] = {
    val later = if (lo >= other.lo) lo else other.lo
    val earlier = if (hi <= other.hi) hi else other.hi
    Option.when(later < earlier)(Interval(later, earlier))
  }

  def +(other: Interval): Interval = Interval(lo.min(other.lo), hi.max(other.hi))

  /** 相邻（如 [0,10) 与 [10,20)）也算相连。 */
  def isConnected(other: Interval): Boolean = lo <= other.hi && other.lo <= hi

  def shift(delta: Timespan): Interval = Interval(lo + delta, hi + delta)
}

object Interval {
  given Ordering[Interval] = Ordering.by(i => (i.lo, i.hi))

  def singleton(time: Instant): Interval = time ~+ 1.micros

  def upTo(time: Instant): Interval = Interval(time, Instant.MaxValue)
}

/** 区间字面量的语法糖。 */
extension (lo: Instant)
  def ~~(hi: Instant): Interval = Interval(lo, hi)

  def ~+(span: Timespan): Interval = {
    val end = lo + span
    Interval(lo.min(end), lo.max(end))
  }

  def ~-(span: Timespan): Interval = lo ~+ (-span)
