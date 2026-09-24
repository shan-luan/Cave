package com.lomekwi.cave.pipeline

import java.io.Serializable
import java.util
import java.util.{AbstractSequentialList, NoSuchElementException}
import scala.compiletime.uninitialized

/**
 * 有序的 filter 链表。链表本身是 {@code Source}（作为头）与各 {@link Filter} 组成的
 * 节点链，并维护相邻端口连接。
 *
 * <pre>
 * source.FilterOut → f1.FilterIn → f1.FilterOut → f2.FilterIn → ...
 * </pre>
 *
 * <p>列表每个元素都是 {@link Filter}；第 0 个元素的 FilterIn 连到头 filter（Source）的
 * FilterOut。链的末端就是最后一个元素自身的 FilterOut（不额外连接端口），
 * {@code Source.get()} 直接从它取数据。添加/移除/重排时自动维护连接。</p>
 *
 * @tparam T filter 处理的帧类型
 */
@SerialVersionUID(1L)
class FilterList[T](private final val head: Filter[? >: T]) extends util.AbstractSequentialList[Filter[? >: T]] with Serializable {
  private final val headEntry: FilterList.Entry = new FilterList.Entry(null)
  private final val tailEntry: FilterList.Entry = new FilterList.Entry(null)
  private var _size: Int = 0

  if (head == null) {
    throw new IllegalArgumentException("head 不能为 null")
  }
  if (head.getFilterOut == null) {
    throw new IllegalArgumentException("head 必须有 FilterOut")
  }
  headEntry.next = tailEntry
  tailEntry.prev = headEntry

  override def size(): Int = {
    _size
  }

  override def listIterator(index: Int): util.ListIterator[Filter[? >: T]] = {
    if (index < 0 || index > _size) {
      throw new IndexOutOfBoundsException("index: " + index + ", size: " + _size)
    }
    new FilterListIterator(index)
  }

  /**
   * 把 filter 插入到 succ 之前，并维护连接。
   * 断开 pred.out → succ.in，改为 pred.out → filter.in → filter.out → succ.in。
   * pred 可能为 headEntry（此时 pred.out = head.FilterOut），
   * succ 可能为 tailEntry（此时 succ.in = tail.FilterIn）。
   */
  private def linkBefore(filter: Filter[? >: T], succ: FilterList.Entry): Unit = {
    if (filter == null) {
      throw new NullPointerException("filter")
    }
    val pred = succ.prev
    val newEntry = new FilterList.Entry(filter.asInstanceOf[Filter[? >: Object]])

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
    if (entry == headEntry) head.getFilterOut
    else if (entry == tailEntry) null
    else entry.filter.asInstanceOf[Filter[? >: T]].getFilterOut
  }

  private def portIn(entry: FilterList.Entry): Node.InPort[?] = {
    // 链末端不连接端口，输出即最后一个 filter 的 FilterOut
    if (entry == tailEntry || entry == headEntry) null
    else entry.filter.asInstanceOf[Filter[? >: T]].getFilterIn
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

  override def add(filter: Filter[? >: T]): Boolean = {
    linkBefore(filter, tailEntry)
    true
  }

  override def add(index: Int, filter: Filter[? >: T]): Unit = {
    // index == size 表示追加到尾部（AbstractSequentialList 语义）
    linkBefore(filter, if (index == _size) tailEntry else entryAt(index))
  }

  override def remove(index: Int): Filter[? >: T] = {
    val entry = entryAt(index)
    val f = entry.filter.asInstanceOf[Filter[? >: T]]
    unlink(entry)
    f
  }

  override def set(index: Int, filter: Filter[? >: T]): Filter[? >: T] = {
    val entry = entryAt(index)
    val old = entry.filter.asInstanceOf[Filter[? >: T]]
    disconnect(entry.prev, entry)
    disconnect(entry, entry.next)
    entry.filter = filter.asInstanceOf[Filter[? >: Object]]
    connect(entry.prev, entry)
    connect(entry, entry.next)
    old.getFilterIn.unlink()
    old.getFilterOut.unlink()
    old
  }

  override def clear(): Unit = {
    var x = headEntry.next
    while (x != tailEntry) {
      val next = x.next
      x.filter.getFilterIn.unlink()
      x.filter.getFilterOut.unlink()
      x.filter = null
      x.prev = null
      x.next = null
      x = next
    }
    headEntry.next = tailEntry
    tailEntry.prev = headEntry
    _size = 0
  }

  private final class FilterListIterator(index: Int) extends util.ListIterator[Filter[? >: T]] {
    private var lastReturned: FilterList.Entry = uninitialized
    private var _next: FilterList.Entry = uninitialized
    private var _nextIndex: Int = 0

    _next = if (index == _size) tailEntry else entryAt(index)
    _nextIndex = index

    override def hasNext: Boolean = {
      _nextIndex < _size
    }

    override def next(): Filter[? >: T] = {
      if (!hasNext) throw new NoSuchElementException()
      lastReturned = _next
      _next = _next.next
      _nextIndex += 1
      lastReturned.filter.asInstanceOf[Filter[? >: T]]
    }

    override def hasPrevious: Boolean = {
      _nextIndex > 0
    }

    override def previous(): Filter[? >: T] = {
      if (!hasPrevious) throw new NoSuchElementException()
      _next = if (_next == null) tailEntry else _next.prev
      lastReturned = _next
      _nextIndex -= 1
      lastReturned.filter.asInstanceOf[Filter[? >: T]]
    }

    override def nextIndex(): Int = {
      _nextIndex
    }

    override def previousIndex(): Int = {
      _nextIndex - 1
    }

    override def remove(): Unit = {
      if (lastReturned == null) throw new IllegalStateException()
      val entry = lastReturned
      lastReturned = null
      unlink(entry)
      _nextIndex -= 1
    }

    override def set(filter: Filter[? >: T]): Unit = {
      if (lastReturned == null) throw new IllegalStateException()
      disconnect(lastReturned.prev, lastReturned)
      disconnect(lastReturned, lastReturned.next)
      lastReturned.filter = filter.asInstanceOf[Filter[? >: Object]]
      connect(lastReturned.prev, lastReturned)
      connect(lastReturned, lastReturned.next)
    }

    override def add(filter: Filter[? >: T]): Unit = {
      lastReturned = null
      linkBefore(filter, _next)
      _nextIndex += 1
    }
  }
}

object FilterList {
  @SerialVersionUID(1L)
  private[pipeline] final class Entry extends Serializable {
    private[pipeline] var filter: Filter[? >: Object] = uninitialized
    private[pipeline] var prev: Entry = uninitialized
    private[pipeline] var next: Entry = uninitialized

    def this(filter: Filter[? >: Object]) = {
      this()
      this.filter = filter
    }
  }
}
