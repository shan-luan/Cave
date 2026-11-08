package com.lomekwi.cave.timeline

import org.bytedeco.ffmpeg.global.avutil.AV_TIME_BASE

opaque type Timespan = Long

object Timespan {
  val MinValue: Timespan = Long.MinValue
  val MaxValue: Timespan = Long.MaxValue
  val Zero: Timespan = 0L

  val MILLISECOND: Timespan = 1_000L
  val SECOND: Timespan = 1_000L * MILLISECOND
  val MINUTE: Timespan = 60L * SECOND
  val HOUR: Timespan = 60L * MINUTE
  val DAY: Timespan = 24L * HOUR

  def apply(value: Long): Timespan = value

  given Ordering[Timespan] = Ordering.Long

  extension (timespan: Timespan) {
    def value: Long = timespan

    def +(other: Timespan): Timespan = timespan + other

    def -(other: Timespan): Timespan = timespan - other

    def unary_- : Timespan = -timespan

    def *(factor: Double): Timespan = Timespan((timespan * factor).toLong)

    def /(factor: Double): Timespan = Timespan((timespan / factor).toLong)

    def /(divisor: Timespan): Long = timespan / divisor

    def %(divisor: Timespan): Timespan = timespan % divisor

    def floorTo(step: Timespan): Timespan = Timespan(timespan / step * step)

    def <(other: Timespan): Boolean = timespan < other

    def <=(other: Timespan): Boolean = timespan <= other

    def >(other: Timespan): Boolean = timespan > other

    def >=(other: Timespan): Boolean = timespan >= other

    def min(other: Timespan): Timespan = if (timespan <= other) timespan else other

    def max(other: Timespan): Timespan = if (timespan >= other) timespan else other

    def abs: Timespan = if (timespan < 0) -timespan else timespan
  }

  extension (value: Int) {
    def micros: Timespan = Timespan(value.toLong)

    def millis: Timespan = Timespan(value.toLong * MILLISECOND)

    def seconds: Timespan = Timespan(value.toLong * SECOND)

    def minutes: Timespan = Timespan(value.toLong * MINUTE)

    def hours: Timespan = Timespan(value.toLong * HOUR)

    def days: Timespan = Timespan(value.toLong * DAY)
  }

  extension (value: Float) {
    def micros: Timespan = Timespan(value.toLong)
  }

  extension (value: Double) {
    def micros: Timespan = Timespan(value.toLong)
  }

  extension (value: Long) {
    def micros: Timespan = Timespan(value)

    def millis: Timespan = Timespan(value * MILLISECOND)

    def seconds: Timespan = Timespan(value * SECOND)

    def minutes: Timespan = Timespan(value * MINUTE)

    def hours: Timespan = Timespan(value * HOUR)

    def days: Timespan = Timespan(value * DAY)

    def avTime: Timespan = Timespan(value * (SECOND / AV_TIME_BASE))
  }
}
