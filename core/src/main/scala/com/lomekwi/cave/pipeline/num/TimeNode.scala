package com.lomekwi.cave.pipeline.num

import com.lomekwi.cave.pipeline.EvalClock
import com.lomekwi.cave.pipeline.Node
import com.lomekwi.cave.util.Units
import com.lomekwi.cave.util.legacy_i18n.I18N.i18n

/** 输出当前求值时刻，单位秒。片段内相对片段起点，时间轴为绝对时间。 */
@SerialVersionUID(1L)
final class TimeNode extends Node {
  final val segmentOut: Node.OutPort[Double] = addOutPort(new Node.OutPort[Double](i18n("片段内"), classOf[Double]) {
    override def getData: Double = EvalClock.segmentTime.toDouble / Units.SECOND
  })

  final val timelineOut: Node.OutPort[Double] = addOutPort(new Node.OutPort[Double](i18n("时间轴"), classOf[Double]) {
    override def getData: Double = EvalClock.timelineTime.toDouble / Units.SECOND
  })

  override val name: String = i18n("时间")
}
