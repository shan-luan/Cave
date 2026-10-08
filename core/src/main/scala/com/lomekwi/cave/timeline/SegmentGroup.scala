package com.lomekwi.cave.timeline

import com.lomekwi.cave.pipeline.Segment

import scala.collection.mutable

/**
 * 一组被同时操作的片段。组可以跨轨道，因此不归属于任何轨道，由 [[Timeline]] 统一登记。
 */
@SerialVersionUID(1L)
class SegmentGroup extends mutable.LinkedHashSet[Segment] with Serializable
