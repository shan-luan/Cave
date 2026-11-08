package com.lomekwi.cave.timeline

import scala.annotation.targetName

opaque type Instant = Long

object Instant {
  val MinValue: Instant = At(Timespan(Long.MinValue))
  val MaxValue: Instant = At(Timespan(Long.MaxValue))
  val Zero: Instant = At(Timespan(0))

  def At(timespan: Timespan): Instant = Timespan.value(timespan)

  given Ordering[Instant] = Ordering.Long

  extension (instant: Instant) {
    def value: Long = instant

    def +(timespan: Timespan): Instant = instant + Timespan.value(timespan)

    @targetName("minusTimespan")
    def -(timespan: Timespan): Instant = instant - Timespan.value(timespan)

    @targetName("minusInstant")
    def -(other: Instant): Timespan = Timespan(instant - other)

    def <(other: Instant): Boolean = instant < other

    def <=(other: Instant): Boolean = instant <= other

    def >(other: Instant): Boolean = instant > other

    def >=(other: Instant): Boolean = instant >= other

    def min(other: Instant): Instant = if (instant <= other) instant else other

    def max(other: Instant): Instant = if (instant >= other) instant else other

    def floorTo(step: Timespan): Instant = At(Timespan((instant / step.value) * step.value))
  }
}
