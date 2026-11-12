package com.lomekwi.cave.pipeline.text

import com.lomekwi.cave.pipeline.Filter
import com.lomekwi.cave.pipeline.{FilterChain, Node, NodeGraph}
import com.lomekwi.cave.util.i18n.*
import com.lomekwi.cave.util.i18n.BasicLang.*

import java.util.regex.{Pattern, PatternSyntaxException}

import scala.compiletime.uninitialized

/**
 * 对 [[TextFrame]] 的文本执行正则替换的滤镜。
 */
@SerialVersionUID(1L)
class TextFilter extends Filter[TextFrame] {
  private[text] final val regexIn: Node.InPort[String] = addInPort(
    new Node.InPort[String](("正则".in(Zh) | "Regex".in(En)).str(), "", classOf[String]))
  private[text] final val replaceIn: Node.InPort[String] = addInPort(
    new Node.InPort[String](("替换".in(Zh) | "Replace".in(En)).str(), "", classOf[String]))

  @transient private var compiled: Pattern = uninitialized
  @transient private var compiledFrom: String = uninitialized

  private final val in: FilterIn = addInPort(new FilterIn(("输入".in(Zh) | "Input".in(En)).str()))

  private final val out: FilterOut = addOutPort(new FilterOut(("输出".in(Zh) | "Output".in(En)).str()) {
    override def getData(context: FilterChain | NodeGraph): TextFrame = {
      val frame: TextFrame = filterIn.getData(context)
      if (frame != null) {
        val text = frame.getText
        val regex = regexIn.getData(context)
        val replacement = replaceIn.getData(context)
        if (text != null && regex != null && replacement != null) {
          try {
            val replaced = patternFor(regex).matcher(text).replaceAll(replacement)
            if (replaced != text) {
              frame.setText(replaced)
            }
          } catch {
            case e: PatternSyntaxException =>
              throw new RuntimeException(e)
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

  def getRegex: String = regexIn.defaultData

  def getReplacement: String = replaceIn.defaultData

  override val name: String = ("文本替换".in(Zh) | "Text Replace".in(En)).str()
}
