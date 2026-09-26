package com.lomekwi.cave.timeline

import com.lomekwi.cave.pipeline.Source

import java.io.Serializable
import java.util

/**
 * 一组被同时操作（拖动/分割）的源。组可以跨轨道，因此不归属于任何轨道，
 * 由 [[Timeline]] 统一登记。
 */
@SerialVersionUID(1L)
class SourceGroup extends util.AbstractCollection[Source[?]] with Serializable {
  private final val sources: util.Set[Source[?]] = new util.LinkedHashSet[Source[?]]()

  override def add(source: Source[?]): Boolean = {
    sources.add(source)
  }

  override def remove(o: Any): Boolean = {
    sources.remove(o)
  }

  override def clear(): Unit = {
    sources.clear()
  }

  override def contains(o: Any): Boolean = {
    sources.contains(o)
  }

  override def isEmpty: Boolean = {
    sources.isEmpty
  }

  override def iterator(): util.Iterator[Source[?]] = {
    sources.iterator()
  }

  override def size(): Int = {
    sources.size()
  }
}
