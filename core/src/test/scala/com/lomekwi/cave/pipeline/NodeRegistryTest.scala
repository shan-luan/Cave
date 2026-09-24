package com.lomekwi.cave.pipeline

import com.lomekwi.cave.pipeline.FilterListTest.FpSrc
import com.lomekwi.cave.pipeline.num.{AddNode, RandomNode}
import org.junit.jupiter.api.Assertions.{assertFalse, assertTrue}
import org.junit.jupiter.api.Test

/**
 * 验证节点注册表的两条消费路径，滤镜链菜单只列 Filter，节点编辑器菜单列全部节点。
 */
class NodeRegistryTest {

  @Test
  def filterMenu_excludesNonFilters(): Unit = {
    val registry = new NodeRegistry()
    val source = new FpSrc(1)
    val compatible = (0 until registry.getCompatibleCount(source))
      .map(i => registry.createCompatible(source, i))

    assertTrue(compatible.nonEmpty, "至少应有节点图滤镜可用")
    assertTrue(compatible.forall(_.isInstanceOf[Filter[?]]), "滤镜链菜单不应包含非 Filter 节点")
    assertFalse(compatible.exists(node => node.isInstanceOf[RandomNode] || node.isInstanceOf[AddNode]))
  }

  @Test
  def nodeEditorMenu_listsGraphOnlyNodes(): Unit = {
    val registry = new NodeRegistry()
    val names = (0 until registry.getCount).map(i => registry.create(i).getName).toSet

    assertTrue(names.contains("随机数"))
    assertTrue(names.contains("加法"))
    assertTrue(names.contains("减法"))
    assertTrue(names.contains("乘法"))
    assertTrue(names.contains("除法"))
  }
}
