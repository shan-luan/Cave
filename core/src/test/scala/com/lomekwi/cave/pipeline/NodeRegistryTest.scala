package com.lomekwi.cave.pipeline

import com.lomekwi.cave.pipeline.FilterListTest.FpCont
import com.lomekwi.cave.pipeline.num.RandomNode

/**
 * 验证节点注册表的滤镜链菜单只列 Filter。
 */
class NodeRegistryTest extends munit.FunSuite {

  test("滤镜菜单排除非滤镜节点") {
    val registry = new NodeRegistry()
    val segment = new FpCont(1)
    val compatible = (0 until registry.getCompatibleCount(segment))
      .map(i => registry.createCompatible(segment, i))

    assert(compatible.nonEmpty, "至少应有节点图滤镜可用")
    assert(compatible.forall(_.isInstanceOf[Filter[?]]), "滤镜链菜单不应包含非 Filter 节点")
    assert(!(compatible.exists(_.isInstanceOf[RandomNode])))
  }
}
