package com.lomekwi.cave.util

object Units {
  private final val KILO = 1_000L
  final val MEGA = KILO * KILO
  private final val GIGA = KILO * MEGA
  private final val TERA = KILO * GIGA
  final val PETA = KILO * TERA

  private final val MICROSECOND = 1//timebase
  private final val MILLISECOND = KILO * MICROSECOND
  final val SECOND = KILO * MILLISECOND
  private final val MINUTE = 60 * SECOND
  private final val HOUR = 60 * MINUTE
  final val DAY = 24 * HOUR

  /**
   * Nice Scale 算法，把原始区间值舍入到最近的"整齐"数（1、2 或 5 × 10^n），
   * 适合作为坐标轴刻度间距。
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
   * niceScale 的浮点版本，把原始区间舍入到最近的"整齐"数（1、2、5、10 × 10^n）。
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

}
