package com.lomekwi.cave.pipeline

import java.io.Serializable

import scala.collection.mutable
import scala.compiletime.uninitialized

/**
 * 有序的 filter 链表。链表本身是 [[Source]]（作为头）与各 [[Filter]] 组成的
 * 节点链，并维护相邻端口连接。
 *
 * ```
 * source.FilterOut → f1.FilterIn → f1.FilterOut → f2.FilterIn → ...
 * ```
 *
 * 列表每个元素都是 [[Filter]]；第 0 个元素的 [[Filter.filterIn]] 连到头（[[Source]]）的
 * [[Filter.filterOut]]。链的末端就是最后一个元素自身的 [[Filter.filterOut]]（不额外连接端口），
 * [[Segment.get]] 直接从它取数据。添加/移除/重排时自动维护连接。
 */
@SerialVersionUID(1L)
class FilterList(private final val headFilter: Filter[?]) extends mutable.Buffer[Filter[?]] with Serializable {
  private final val headEntry: FilterList.Entry = new FilterList.Entry(null)
  private final val tailEntry: FilterList.Entry = new FilterList.Entry(null)
  private var _size: Int = 0

  if (headFilter == null) {
    throw new IllegalArgumentException("head 不能为 null")
  }
  if (headFilter.filterOut == null) {
    throw new IllegalArgumentException("head 必须有 FilterOut")
  }
  headEntry.next = tailEntry
  tailEntry.prev = headEntry

  override def length: Int = _size

  override def apply(index: Int): Filter[?] = entryAt(index).filter

  /** 替换 index 处的 filter，旧 filter 的端口连接被断开。 */
  override def update(index: Int, filter: Filter[?]): Unit = {
    val entry = entryAt(index)
    val old = entry.filter
    disconnect(entry.prev, entry)
    disconnect(entry, entry.next)
    entry.filter = filter
    connect(entry.prev, entry)
    connect(entry, entry.next)
    old.filterIn.unlink()
    old.filterOut.unlink()
  }

  /** 在 index 处插入 filter 并维护连接。index == size 表示追加到尾部。 */
  override def insert(index: Int, filter: Filter[?]): Unit = {
    if (index < 0 || index > _size) {
      throw new IndexOutOfBoundsException("index: " + index + ", size: " + _size)
    }
    linkBefore(filter, if (index == _size) tailEntry else entryAt(index))
  }

  override def remove(index: Int): Filter[?] = {
    val entry = entryAt(index)
    val f = entry.filter
    unlink(entry)
    f
  }

  override def insertAll(index: Int, elems: IterableOnce[Filter[?]]): Unit = {
    if (index < 0 || index > _size) {
      throw new IndexOutOfBoundsException("index: " + index + ", size: " + _size)
    }
    var i = index
    for (f <- elems.iterator) {
      insert(i, f)
      i += 1
    }
  }

  override def addOne(elem: Filter[?]): this.type = {
    insert(_size, elem)
    this
  }

  override def prepend(elem: Filter[?]): this.type = {
    insert(0, elem)
    this
  }

  override def remove(index: Int, count: Int): Unit = {
    var c = count
    while (c > 0 && index < _size) {
      remove(index)
      c -= 1
    }
  }

  override def patchInPlace(from: Int, patch: IterableOnce[Filter[?]], replaced: Int): this.type = {
    remove(from, replaced)
    insertAll(from, patch)
    this
  }

  override def clear(): Unit = {
    var x = headEntry.next
    while (x != tailEntry) {
      val next = x.next
      x.filter.filterIn.unlink()
      x.filter.filterOut.unlink()
      x.filter = null
      x.prev = null
      x.next = null
      x = next
    }
    headEntry.next = tailEntry
    tailEntry.prev = headEntry
    _size = 0
  }

  /**
   * 把 filter 插入到 succ 之前，并维护连接。
   * pred 可能为 [[headEntry]]（此时 pred.out = [[head.filterOut]]），
   * succ 可能为 tailEntry（此时 succ.in = tail.FilterIn）。
   */
  private def linkBefore(filter: Filter[?], succ: FilterList.Entry): Unit = {
    // 断开 pred.out → succ.in，改为 pred.out → filter.in → filter.out → succ.in
    if (filter == null) {
      throw new NullPointerException("filter")
    }
    val pred = succ.prev
    val newEntry = new FilterList.Entry(filter)

    disconnect(pred, succ)

    newEntry.prev = pred
    newEntry.next = succ
    pred.next = newEntry
    succ.prev = newEntry
    _size += 1

    connect(pred, newEntry)
    connect(newEntry, succ)
  }

  /** 删除节点，重连 pred.out → next.in。 */
  private def unlink(entry: FilterList.Entry): Unit = {
    val pred = entry.prev
    val next = entry.next
    pred.next = next
    next.prev = pred
    disconnect(pred, entry)
    disconnect(entry, next)
    connect(pred, next)

    entry.filter = null
    entry.prev = null
    entry.next = null
    _size -= 1
  }

  /** pred.out 与 succ.in 之间的连接（pred/succ 可能是哨兵）。 */
  private def connect(pred: FilterList.Entry, succ: FilterList.Entry): Unit = {
    val in: Node.InPort[?] = portIn(succ)
    val out: Node.OutPort[?] = portOut(pred)
    if (in != null && out != null) {
      in.asInstanceOf[Node.InPort[Any]].linkFrom(out.asInstanceOf[Node.OutPort[Any]])
    }
  }

  /** 断开 pred.out 与 succ.in 之间的连接（pred/succ 可能是哨兵）。 */
  private def disconnect(pred: FilterList.Entry, succ: FilterList.Entry): Unit = {
    val in: Node.InPort[?] = portIn(succ)
    if (in != null) in.unlink()
  }

  private def portOut(entry: FilterList.Entry): Node.OutPort[?] = {
    if (entry == headEntry) headFilter.filterOut
    else if (entry == tailEntry) null
    else entry.filter.filterOut
  }

  private def portIn(entry: FilterList.Entry): Node.InPort[?] = {
    // 链末端不连接端口，输出即最后一个 filter 的 [[Filter.filterOut]]
    if (entry == tailEntry || entry == headEntry) null
    else entry.filter.filterIn
  }

  private def entryAt(index: Int): FilterList.Entry = {
    if (index < 0 || index >= _size) {
      throw new IndexOutOfBoundsException("index: " + index + ", size: " + _size)
    }
    var x: FilterList.Entry = null
    if (index < _size / 2) {
      x = headEntry.next
      var i = 0
      while (i < index) {
        x = x.next
        i += 1
      }
    } else {
      x = tailEntry
      var i = _size
      while (i > index) {
        x = x.prev
        i -= 1
      }
    }
    x
  }

  override def iterator: Iterator[Filter[?]] = new Iterator[Filter[?]] {
    private var cursor: FilterList.Entry = headEntry.next

    override def hasNext: Boolean = cursor != tailEntry

    override def next(): Filter[?] = {
      if (!hasNext) throw new java.util.NoSuchElementException()
      val f = cursor.filter
      cursor = cursor.next
      f
    }
  }
}

object FilterList {
  @SerialVersionUID(1L)
  private[pipeline] final class Entry extends Serializable {
    private[pipeline] var filter: Filter[?] = uninitialized
    private[pipeline] var prev: Entry = uninitialized
    private[pipeline] var next: Entry = uninitialized

    def this(filter: Filter[?]) = {
      this()
      this.filter = filter
    }
  }
}
