package com.lomekwi.cave.util

import com.google.common.collect.Range

object Ranges {
  def shift(r: Range[java.lang.Long], delta: Long): Range[java.lang.Long] = {
    Range.closedOpen(java.lang.Long.valueOf(r.lowerEndpoint() + delta), java.lang.Long.valueOf(r.upperEndpoint() + delta))
  }
}
