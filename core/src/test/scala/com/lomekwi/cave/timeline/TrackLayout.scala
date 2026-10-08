package com.lomekwi.cave.timeline

import com.lomekwi.cave.pipeline.{Content, Transition}

import org.junit.jupiter.api.Assertions.*


/**
 * 轨道布局不变量校验，供各测试复用。删除路径的场景测试与随机拖拽场景都依赖它兜住
 * 布局被破坏的情况。
 */
object TrackLayout {

  /** 轨道上按时间排列的全部内容。 */
  def contentsOf(track: Track): Vector[Content] =
    track.collect { case c: Content => c }.toVector

  /** 相邻内容交叉、隔项不重叠、转场区间恒等于重叠区。 */
  def assertValid(track: Track): Unit = {
    val contents = contentsOf(track)
    var i = 0
    while (i < contents.size) {
      val cr = track.getRange(contents(i))
      if (i + 1 < contents.size) {
        val nr = track.getRange(contents(i + 1))
        if (cr.hi > nr.lo) {
          assertTrue(cr.lo < nr.lo && cr.hi < nr.hi, "相邻内容未交叉: " + cr + " " + nr)
        }
      }
      if (i + 2 < contents.size) {
        val nn = track.getRange(contents(i + 2))
        assertFalse(cr.hi > nn.lo, "隔项重叠: " + cr + " " + nn)
      }
      i += 1
    }
    for (s <- track) {
      s match {
        case t: Transition =>
          val sides = track.transitionSides(t)
          assertNotNull(sides, "转场两侧缺失")
          if (sides != null) {
            val lo = track.getRange(sides._2).lo
            val hi = track.getRange(sides._1).hi
            assertTrue(lo < hi, "转场两侧没有重叠")
            assertEquals(lo ~~ hi, track.getRange(t), "转场区间不是重叠区")
          }
        case _ =>
      }
    }
  }
}
