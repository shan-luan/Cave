package com.lomekwi.cave.timeline

/**
 * 轨道布局不变量校验，供各测试复用。删除路径的场景测试与随机拖拽场景都依赖它兜住
 * 布局被破坏的情况。
 */
object TrackLayout extends munit.Assertions {

  /** 轨道上按时间排列的全部内容。 */
  def contentsOf(track: Track): Vector[Content] =
    track.collect { case c: Content => c }.toVector

  /** 相邻内容交叉、隔项不重叠、转场区间恒等于重叠区。 */
  def assertValid(track: Track): Unit = {
    val contents = contentsOf(track)
    var i = 0
    while (i < contents.size) {
      val cr = track.getInterval(contents(i))
      if (i + 1 < contents.size) {
        val nr = track.getInterval(contents(i + 1))
        if (cr.hi > nr.lo) {
          assert(cr.lo < nr.lo && cr.hi < nr.hi, "相邻内容未交叉: " + cr + " " + nr)
        }
      }
      if (i + 2 < contents.size) {
        val nn = track.getInterval(contents(i + 2))
        assert(!(cr.hi > nn.lo), "隔项重叠: " + cr + " " + nn)
      }
      i += 1
    }
    for (s <- track) {
      s match {
        case t: Transition =>
          track.transitionSides(t) match {
            case Some((left, right)) =>
              val lo = track.getInterval(right).lo
              val hi = track.getInterval(left).hi
              assert(lo < hi, "转场两侧没有重叠")
              assertEquals(track.getInterval(t), lo ~~ hi, "转场区间不是重叠区")
            case None => fail("转场两侧缺失")
          }
        case _ =>
      }
    }
  }
}
