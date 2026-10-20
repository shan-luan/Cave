package com.lomekwi.cave.pipeline.audio

import com.lomekwi.cave.pipeline.Filter
import com.lomekwi.cave.pipeline.Node
import com.lomekwi.cave.util.i18n.*
import com.lomekwi.cave.util.i18n.BasicLang.*
import com.lomekwi.cave.util.i18n.TransTargetType.given

@SerialVersionUID(1L)
class GainNode extends Filter[AudFrame] {
  private final val gain: Node.InPort[Double] = addInPort(
    new Node.InPort[Double](("音量".in(Zh) | "Volume".in(En)).str(), 1.0, classOf[Double]))

  private final val in: FilterIn = addInPort(new FilterIn(("输入".in(Zh) | "Input".in(En)).str()))

  private final val out: FilterOut = addOutPort(new FilterOut(("输出".in(Zh) | "Output".in(En)).str()) {
    override def getData: AudFrame = {
      val frame: AudFrame = filterIn.getData
      if (frame != null) {
        val gainValue = gain.getData.toFloat
        val samples = frame.samples
        var i = 0
        while (i < samples.length) {
          samples(i) *= gainValue
          i += 1
        }
      }
      frame
    }
  })

  def getGain: Double = {
    gain.getData
  }

  def setGain(v: Double): Unit = {
    gain.defaultData = v
  }

  override val name: String = ("音量".in(Zh) | "Volume".in(En)).str()
}
