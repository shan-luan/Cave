package com.lomekwi.cave.ui.node

import com.kotcrab.vis.ui.widget.VisLabel
import com.lomekwi.cave.pipeline.EvalClock
import com.lomekwi.cave.pipeline.Node

/**
 * 输出端口的只读显示行，仅由 inspector 的源信息卡使用，节点编辑器按设计不显示输出行。
 * 文本在构造时一次性采样，采样在时刻 0 的求值上下文中进行，依赖求值上下文的节点显示该时刻的值。
 */
final class FpOutputRow(port0: Node.OutPort[?]) extends VisLabel(port0.name + ": " + EvalClock.withTime(0L, 0L)(port0.asInstanceOf[Node.OutPort[Double]].getData)) with PortRow {
  override final val port: Node.OutPort[?] = port0
}
