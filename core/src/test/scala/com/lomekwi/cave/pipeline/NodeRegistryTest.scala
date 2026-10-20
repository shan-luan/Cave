package com.lomekwi.cave.pipeline

import com.lomekwi.cave.pipeline.FilterListTest.FpCont
import com.lomekwi.cave.pipeline.num.{AddNode, RandomNode}
import org.junit.jupiter.api.Assertions.{assertFalse, assertTrue}
import org.junit.jupiter.api.Test

/**
 * 验证节点注册表的滤镜链菜单只列 Filter。
 */
class NodeRegistryTest {

  @Test
  def filterMenu_excludesNonFilters(): Unit = {
    val registry = new NodeRegistry()
    val segment = new FpCont(1)
    val compatible = (0 until registry.getCompatibleCount(segment))
      .map(i => registry.createCompatible(segment, i))

    assertTrue(compatible.nonEmpty, "至少应有节点图滤镜可用")
    assertTrue(compatible.forall(_.isInstanceOf[Filter[?]]), "滤镜链菜单不应包含非 Filter 节点")
    assertFalse(compatible.exists(node => node.isInstanceOf[RandomNode] || node.isInstanceOf[AddNode]))
  }
}
