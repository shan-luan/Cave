package com.lomekwi.cave.util

import com.google.common.collect.Range

object Ranges {
  def shift(r: Range[java.lang.Long], delta: Long): Range[java.lang.Long] = {
    Range.closedOpen(r.lowerEndpoint() + delta, r.upperEndpoint() + delta)
  }
}
