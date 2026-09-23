package com.lomekwi.cave.pipeline

import com.lomekwi.cave.pipeline.FilterListTest.{Fpable, FpSrc}
import org.junit.jupiter.api.Assertions.{assertEquals, assertNotNull, assertNotSame, assertNull, assertSame, assertThrows}
import org.junit.jupiter.api.Test

import java.util

/**
 * 验证 Mixer 抽象节点：两个主输入 MixIn 与一个主输出 MixOut 的数量约束、类型推断与求值。
 */
class MixerTest {

  @Test
  def hasTwoInputsAndOneOutput(): Unit = {
    val mixer = new SumMixer()
    assertEquals(2, mixer.getInPorts.size())
    assertEquals(1, mixer.getOutPorts.size())
    assertNotSame(mixer.getMixInA, mixer.getMixInB)
    assertSame(mixer.getMixInA, mixer.getInPorts.get(0))
    assertSame(mixer.getMixInB, mixer.getInPorts.get(1))
    assertSame(mixer.getMixOut, mixer.getOutPorts.get(0))
  }

  @Test
  def thirdMixIn_throws(): Unit = {
    assertThrows(classOf[IllegalStateException], () => new ThreeInMixer())
  }

  @Test
  def secondMixOut_throws(): Unit = {
    assertThrows(classOf[IllegalStateException], () => new TwoOutMixer())
  }

  @Test
  def outputType_unknownWhenNoInputLinked(): Unit = {
    assertNull(new SumMixer().getMixOut.getType)
  }

  @Test
  def outputType_followsInputA(): Unit = {
    val src = new FpSrc(1)
    val mixer = new SumMixer()
    mixer.getMixInA.linkFrom(src.headOut)
    assertSame(classOf[Fpable], mixer.getMixOut.getType)
  }

  @Test
  def outputType_fallsBackToInputB(): Unit = {
    val src = new FpSrc(1)
    val mixer = new SumMixer()
    mixer.getMixInB.linkFrom(src.headOut)
    assertSame(classOf[Fpable], mixer.getMixOut.getType)
  }

  @Test
  def constraint_mergesDownstream(): Unit = {
    val mixer = new SumMixer()
    assertEquals(util.Set.of(classOf[Fpable]), mixer.getMixInA.getConstraint)

    val downstream = new Node.InPort[AnyRef]("下游", classOf[AnyRef])
    mixer.getMixOut.link(downstream)
    assertEquals(util.Set.of(classOf[Fpable], classOf[AnyRef]), mixer.getMixInA.getConstraint)
    assertEquals(util.Set.of(classOf[Fpable], classOf[AnyRef]), mixer.getMixInB.getConstraint)
  }

  @Test
  def evaluatesBothInputs(): Unit = {
    val a = new FpSrc(2)
    val b = new FpSrc(3)
    val mixer = new SumMixer()
    mixer.getMixInA.linkFrom(a.headOut)
    mixer.getMixInB.linkFrom(b.headOut)
    a.get(0, null)
    b.get(0, null)

    assertEquals(5.0, mixer.getMixOut.getData.`val`, 0)
  }
}

/** 最小可测混合器：把两个输入的 val 相加。 */
private final class SumMixer extends Mixer[Fpable] {
  private final val inA: MixIn = addInPort(new MixIn("A"))
  private final val inB: MixIn = addInPort(new MixIn("B"))
  private final val out: MixOut = addOutPort(new MixOut("输出") {
    override def getData: Fpable = {
      val a = inA.getData
      val b = inB.getData
      if (a != null && b != null) {
        a.`val` += b.`val`
      }
      a
    }
  })

  override def getName: String = "求和混合"
}

private final class ThreeInMixer extends Mixer[Fpable] {
  addInPort(new MixIn("A"))
  addInPort(new MixIn("B"))
  addInPort(new MixIn("C"))

  override def getName: String = "三入混合"
}

private final class TwoOutMixer extends Mixer[Fpable] {
  addInPort(new MixIn("A"))
  addInPort(new MixIn("B"))
  addOutPort(new MixOut("输出") {
    override def getData: Fpable = null
  })
  addOutPort(new MixOut("输出2") {
    override def getData: Fpable = null
  })

  override def getName: String = "双出混合"
}
