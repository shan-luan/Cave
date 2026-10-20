package com.lomekwi.cave.timeline

import java.io.Serializable

import com.lomekwi.cave.util.i18n.*
import com.lomekwi.cave.util.i18n.BasicLang.*
import com.lomekwi.cave.util.i18n.TransTargetType.given

/** 左闭右开区间 [lo, hi)，lo == hi 时为空区间。 */
@SerialVersionUID(1L)
case class Interval(lo: Long, hi: Long) extends Serializable {
  require(lo <= hi, s"${("区间下界不得大于上界: ".in(Zh) | "The lower bound of an interval must not exceed its upper bound: ".in(En)).str()}[$lo, $hi)")

  def isEmpty: Boolean = lo == hi

  def len: Long = hi-lo

  def contains(time: Long): Boolean = lo <= time && time < hi

  def intersects(other: Interval): Boolean = lo < other.hi && other.lo < hi

  /** 与另一区间的交集；不相交或仅相邻时为 None。 */
  def &(other: Interval): Option[Interval] = {
    val later = Math.max(lo, other.lo)
    val earlier = Math.min(hi, other.hi)
    Option.when(later < earlier)(Interval(later, earlier))
  }

  def span(other: Interval): Interval = Interval(Math.min(lo, other.lo), Math.max(hi, other.hi))

  /** 相邻（如 [0,10) 与 [10,20)）也算相连。 */
  def isConnected(other: Interval): Boolean = lo <= other.hi && other.lo <= hi

  def shift(delta: Long): Interval = Interval(lo + delta, hi + delta)
}

object Interval {
  given Ordering[Interval] = Ordering.by(i => (i.lo, i.hi))

  /**
   * 实参为MaxValue时会溢出然后抛异常。
   */
  def at(time: Long): Interval = Interval(time, time+1)

  def upTo(time: Long): Interval = Interval(time, Long.MaxValue)
}

/** 区间字面量的语法糖，`1 ~~ 10` 等价于 `Interval(1, 10)`。 */
extension (lo: Int)
  def ~~(hi: Long): Interval = Interval(lo, hi)

extension (lo: Long)
  def ~~(hi: Long): Interval = Interval(lo, hi)
