package com.lomekwi.cave.util

/**
 * Nice Scale 算法，把原始区间值舍入到最近的"整齐"数（1、2 或 5 × 10^n）。
 */
def niceScale(raw: Long): Long = {
  val mag = Math.pow(10, Math.floor(Math.log10(raw.toDouble)))
  val r = raw / mag
  if (r < 2) {
    mag.toLong
  } else if (r < 5) {
    (2 * mag).toLong
  } else {
    (5 * mag).toLong
  }
}

/**
 * [[niceScale]] 的浮点版本，把原始区间舍入到最近的"整齐"数（1、2、5、10 × 10^n）。
 */
def niceInterval(raw: Float): Float = {
  val mag = Math.pow(10, Math.floor(Math.log10(Math.max(raw, 1e-10f)))).toFloat
  val r = raw / mag
  if (r < 1.5f) {
    mag
  } else if (r < 3.5f) {
    2f * mag
  } else if (r < 7.5f) {
    5f * mag
  } else {
    10f * mag
  }
}
