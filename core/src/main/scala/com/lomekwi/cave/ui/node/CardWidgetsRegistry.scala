package com.lomekwi.cave.ui.node

import com.badlogic.gdx.scenes.scene2d.Actor
import com.lomekwi.cave.pipeline.Node
import com.lomekwi.cave.pipeline.NodeGraph
import com.lomekwi.cave.timeline.Segment

import scala.collection.mutable

/**
 * 输入端口默认值编辑器和输出端口显示行的 widget 注册表
 */
class CardWidgetsRegistry {
  import CardWidgetsRegistry.*

  private final val IN_ENTRIES: mutable.ArrayBuffer[InEntry] = mutable.ArrayBuffer.empty[InEntry]
  private final val OUT_ENTRIES: mutable.ArrayBuffer[OutEntry] = mutable.ArrayBuffer.empty[OutEntry]

  registerIn(classOf[Double], (port, segment) => new NumPortEditor(port, segment))
  registerIn(classOf[String], (port, segment) => new TextPortEditor(port, segment))
  registerIn(classOf[NodeGraph], (port, segment) => new NodeGraphPortEditor(port, segment))
  registerOut(classOf[Double], port => new FpOutputRow(port))

  /** 注册输入端口的 widget 工厂。 */
  private def registerIn(`type`: Class[?], factory: (Node.InPort[?], Segment) => Actor): Unit = {
    IN_ENTRIES += InEntry(`type`, factory)
  }

  /** 注册输出端口的只读显示工厂。 */
  private def registerOut(`type`: Class[?], factory: Node.OutPort[?] => Actor): Unit = {
    OUT_ENTRIES += OutEntry(`type`, factory)
  }

  /** 为输入端口创建编辑 widget。 */
  def createEditor(port: Node.InPort[?], segment: Segment): Actor = {
    IN_ENTRIES
      .find(entry => accepts(port, entry.`type`))
      .map(entry => entry.factory(port, segment))
      .orNull
  }

  /** 为输出端口创建只读显示行。 */
  def createOutputRow(port: Node.OutPort[?]): Actor = {
    val `type` = port.getType
    if (`type` == null) {
      null
    } else {
      OUT_ENTRIES
        .find(entry => entry.`type`.isAssignableFrom(`type`))
        .map(entry => entry.factory(port))
        .orNull
    }
  }
}

object CardWidgetsRegistry {
  private case class InEntry(`type`: Class[?], factory: (Node.InPort[?], Segment) => Actor)

  private case class OutEntry(`type`: Class[?], factory: Node.OutPort[?] => Actor)

  private def accepts(port: Node.InPort[?], `type`: Class[?]): Boolean = {
    val constraint = port.constraint
    constraint.nonEmpty && constraint.forall(c => c.isAssignableFrom(`type`))
  }
}
