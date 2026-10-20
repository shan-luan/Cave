package com.lomekwi.cave.pipeline.num

import com.lomekwi.cave.pipeline.EvalClock
import com.lomekwi.cave.pipeline.Node
import com.lomekwi.cave.util.Units
import com.lomekwi.cave.util.i18n.*
import com.lomekwi.cave.util.i18n.BasicLang.*
import com.lomekwi.cave.util.i18n.TransTargetType.given

/** 输出当前求值时刻，单位秒。片段内相对片段起点，时间轴为绝对时间。 */
@SerialVersionUID(1L)
final class TimeNode extends Node {
  val segmentOut: Node.OutPort[Double] = addOutPort(new Node.OutPort[Double](("片段内".in(Zh) | "In Segment".in(En)).str(), classOf[Double]) {
    override def getData: Double = EvalClock.segmentTime.toDouble / Units.SECOND
  })

  val timelineOut: Node.OutPort[Double] = addOutPort(new Node.OutPort[Double](("时间轴".in(Zh) | "Timeline".in(En)).str(), classOf[Double]) {
    override def getData: Double = EvalClock.timelineTime.toDouble / Units.SECOND
  })

  override val name: String = ("时间".in(Zh) | "Time".in(En)).str()
}
