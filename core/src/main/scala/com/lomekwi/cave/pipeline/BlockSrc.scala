package com.lomekwi.cave.pipeline

import com.lomekwi.cave.timeline.Track
import com.lomekwi.cave.ui.editpanel.tlarea.{TlBlockSrcActor, TlSrcActor}

/**
 * 阻挡源。它填充时间轴 0 点左侧，使左边界由普通障碍表达，
 * 拖拽与裁切因此不必再单独判断"不能小于 0"。
 */
class BlockSrc extends Content[Frame] {

  override def sync(time: Long, track: Track): Unit = {}

  override protected def generate(time: Long, track: Track): Frame = new GapFrame(track)

  override def getLengthPerExportFrame: Long = 0L

  override def getDuration: Long = Long.MaxValue

  override def getDisplayName: String = ""

  override def createTlSrcActor(): TlSrcActor = new TlBlockSrcActor(this)
}
