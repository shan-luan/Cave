package com.lomekwi.cave.pipeline.text

import com.lomekwi.cave.pipeline.Filter
import com.lomekwi.cave.pipeline.Node

import java.util.regex.{Pattern, PatternSyntaxException}

import scala.compiletime.uninitialized

/**
 * 对 [[TextFrame]] 的文本执行正则替换的滤镜。对帧文本做 `replaceAll`，替换结果与
 * 原文本相同时不重写帧，避免触发无谓的重排版。正则非法时保持原文本。
 */
@SerialVersionUID(1L)
class TextFilter extends Filter[TextFrame] {
  private final val regexIn: Node.InPort[String] = addInPort(
    new Node.InPort[String]("正则", "", classOf[String]))
  private final val replaceIn: Node.InPort[String] = addInPort(
    new Node.InPort[String]("替换", "", classOf[String]))

  @transient private var compiled: Pattern = uninitialized
  @transient private var compiledFrom: String = uninitialized

  private final val in: FilterIn = addInPort(new FilterIn("输入"))

  private final val out: FilterOut = addOutPort(new FilterOut("输出") {
    override def getData: TextFrame = {
      val frame: TextFrame = filterIn.getData
      if (frame != null) {
        val text = frame.getText
        val regex = regexIn.getData
        val replacement = replaceIn.getData
        if (text != null && regex != null && replacement != null) {
          try {
            val replaced = patternFor(regex).matcher(text).replaceAll(replacement)
            if (replaced != text) {
              frame.setText(replaced)
            }
          } catch {
            case _: PatternSyntaxException =>
          }
        }
      }
      frame
    }
  })

  private def patternFor(regex: String): Pattern = {
    if (compiled == null || compiledFrom != regex) {
      compiled = Pattern.compile(regex)
      compiledFrom = regex
    }
    compiled
  }

  def getRegex: String = regexIn.getData

  def setRegex(v: String): Unit = {
    regexIn.defaultData = v
  }

  def getReplacement: String = replaceIn.getData

  def setReplacement(v: String): Unit = {
    replaceIn.defaultData = v
  }

  override val name: String = "文本替换"
}
