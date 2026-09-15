package com.lomekwi.cave.ui.editpanel.inspector

import com.badlogic.gdx.scenes.scene2d.Actor
import com.lomekwi.cave.pipeline.Node
import com.lomekwi.cave.pipeline.NodeGraph
import com.lomekwi.cave.pipeline.Source
import com.lomekwi.cave.pipeline.num.NumFrame
import com.lomekwi.cave.ui.node.NodeGraphPortEditor
import com.lomekwi.cave.ui.node.NumOutputRow
import com.lomekwi.cave.ui.node.NumPortEditor
import com.lomekwi.cave.ui.node.TextPortEditor

import java.util
import java.util.function.BiFunction
import java.util.function.Function

import scala.jdk.CollectionConverters.*

/**
 * 输入端口默认值编辑器和输出端口显示行的 widget 注册表
 */
object CardWidgetsRegistry {
  private case class InEntry(`type`: Class[?], factory: BiFunction[Node.InPort[?], Source[?], Actor])

  private case class OutEntry(`type`: Class[?], factory: Function[Node.OutPort[?], Actor])

  private final val IN_ENTRIES: util.List[InEntry] = new util.ArrayList[InEntry]()
  private final val OUT_ENTRIES: util.List[OutEntry] = new util.ArrayList[OutEntry]()

  registerIn(classOf[NumFrame], (port, source) => new NumPortEditor(port, source))
  registerIn(classOf[String], (port, source) => new TextPortEditor(port, source))
  registerIn(classOf[NodeGraph], (port, source) => new NodeGraphPortEditor(port, source))
  registerOut(classOf[NumFrame], port => new NumOutputRow(port))

  /** 注册输入端口的 widget 工厂。目标类型为端口约束需能容纳的类型。 */
  private def registerIn(`type`: Class[?], factory: BiFunction[Node.InPort[?], Source[?], Actor]): Unit = {
    IN_ENTRIES.add(InEntry(`type`, factory))
  }

  /** 注册输出端口的只读显示工厂。目标类型为端口声明类型的父类。 */
  private def registerOut(`type`: Class[?], factory: Function[Node.OutPort[?], Actor]): Unit = {
    OUT_ENTRIES.add(OutEntry(`type`, factory))
  }

  /** 为输入端口创建编辑 widget；没有注册对应类型的返回 null（该端口不显示）。 */
  def createEditor(port: Node.InPort[?], source: Source[?]): Actor = {
    IN_ENTRIES.asScala
      .find(entry => accepts(port, entry.`type`))
      .map(entry => entry.factory.apply(port, source))
      .orNull
  }

  /** 为输出端口创建只读显示行；没有注册对应类型的返回 null（该端口不显示）。 */
  def createOutputRow(port: Node.OutPort[?]): Actor = {
    val `type` = port.getType
    if (`type` == null) {
      null
    } else {
      OUT_ENTRIES.asScala
        .find(entry => entry.`type`.isAssignableFrom(`type`))
        .map(entry => entry.factory.apply(port))
        .orNull
    }
  }

  /** 端口约束为交叉类型：目标类型必须满足全部约束。 */
  private def accepts(port: Node.InPort[?], `type`: Class[?]): Boolean = {
    port.getConstraint.asScala.forall(c => c.isAssignableFrom(`type`))
  }
}
