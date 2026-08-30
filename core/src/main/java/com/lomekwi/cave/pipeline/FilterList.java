package com.lomekwi.cave.pipeline;

import java.io.Serial;
import java.io.Serializable;
import java.util.AbstractSequentialList;
import java.util.ListIterator;
import java.util.NoSuchElementException;

/**
 * 有序的 filter 链表。链表本身是 {@code Source}（作为头）与各 {@link Filter} 组成的
 * 节点链，并维护相邻端口连接：
 *
 * <pre>
 * source.FilterOut → f1.FilterIn → f1.FilterOut → f2.FilterIn → ...
 * </pre>
 *
 * <p>列表每个元素都是 {@link Filter}；第 0 个元素的 FilterIn 连到头 filter（Source）的
 * FilterOut。链的末端就是最后一个元素自身的 FilterOut（不额外连接端口），
 * {@code Source.get()} 直接从它取数据。添加/移除/重排时自动维护连接。</p>
 *
 * @param <T> filter 处理的帧类型
 */
public class FilterList<T> extends AbstractSequentialList<Filter<? super T>> implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /** 头 filter（Source）：其 FilterOut 是链的起点。 */
    private final Filter<? super T> head;

    private final Entry headEntry = new Entry(null);
    private final Entry tailEntry = new Entry(null);
    private int size;

    public FilterList(Filter<? super T> head) {
        if (head == null) {
            throw new IllegalArgumentException("head 不能为 null");
        }
        if (head.getFilterOut() == null) {
            throw new IllegalArgumentException("head 必须有 FilterOut");
        }
        this.head = head;
        headEntry.next = tailEntry;
        tailEntry.prev = headEntry;
    }

    // ──────────────── AbstractSequentialList 接口 ────────────────

    @Override
    public int size() {
        return size;
    }

    @Override
    public ListIterator<Filter<? super T>> listIterator(int index) {
        if (index < 0 || index > size) {
            throw new IndexOutOfBoundsException("index: " + index + ", size: " + size);
        }
        return new FilterListIterator(index);
    }

    // ──────────────── 链表结构 ────────────────

    private static final class Entry implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        Filter<? super Object> filter;
        Entry prev;
        Entry next;

        Entry(Filter<? super Object> filter) {
            this.filter = filter;
        }
    }

    /**
     * 把 filter 插入到 succ 之前，并维护连接：
     * 断开 pred.out → succ.in，改为 pred.out → filter.in → filter.out → succ.in。
     * pred 可能为 headEntry（此时 pred.out = head.FilterOut），
     * succ 可能为 tailEntry（此时 succ.in = tail.FilterIn）。
     */
    private void linkBefore(Filter<? super T> filter, Entry succ) {
        if (filter == null) {
            throw new NullPointerException("filter");
        }
        Entry pred = succ.prev;
        Entry newEntry = new Entry((Filter<? super Object>) filter);

        // 断开旧连接 pred.out → succ.in
        disconnect(pred, succ);

        // 组装链表
        newEntry.prev = pred;
        newEntry.next = succ;
        pred.next = newEntry;
        succ.prev = newEntry;
        size++;

        // 挂载到所属源
        filter.setSource((Source<?>) head);

        // 建立新连接 pred.out → filter.in → filter.out → succ.in
        connect(pred, newEntry);
        connect(newEntry, succ);
    }

    /** 删除节点，重连 pred.out → next.in。 */
    private void unlink(Entry entry) {
        Entry pred = entry.prev;
        Entry next = entry.next;
        // 更新链表指针：把 entry 从链中摘除
        pred.next = next;
        next.prev = pred;
        // 断开 entry.in ← pred.out 与 entry.out → next.in
        disconnect(pred, entry);
        disconnect(entry, next);
        // 重连 pred.out → next.in
        connect(pred, next);

        // 解除挂载
        ((Filter<? super T>) entry.filter).setSource(null);
        entry.filter = null;
        entry.prev = null;
        entry.next = null;
        size--;
    }

    /** pred.out 与 succ.in 之间的连接（pred/succ 可能是哨兵）。 */
    private void connect(Entry pred, Entry succ) {
        Node.InPort<?> in = portIn(succ);
        Node.OutPort<?> out = portOut(pred);
        if (in == null || out == null) return;
        in.linkFrom(out);
    }

    /** 断开 pred.out 与 succ.in 之间的连接（pred/succ 可能是哨兵）。 */
    private void disconnect(Entry pred, Entry succ) {
        Node.InPort<?> in = portIn(succ);
        if (in != null) in.unlink();
    }

    private Node.OutPort<?> portOut(Entry entry) {
        if (entry == headEntry) return head.getFilterOut();
        if (entry == tailEntry) return null;
        return ((Filter<? super T>) entry.filter).getFilterOut();
    }

    private Node.InPort<?> portIn(Entry entry) {
        if (entry == tailEntry) return null; // 链末端不连接端口，输出即最后一个 filter 的 FilterOut
        if (entry == headEntry) return null;
        return ((Filter<? super T>) entry.filter).getFilterIn();
    }

    private Entry entryAt(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("index: " + index + ", size: " + size);
        }
        Entry x;
        if (index < size / 2) {
            x = headEntry.next;
            for (int i = 0; i < index; i++) x = x.next;
        } else {
            x = tailEntry;
            for (int i = size; i > index; i--) x = x.prev;
        }
        return x;
    }

    // ──────────────── List 变体（保持连接） ────────────────

    @Override
    public boolean add(Filter<? super T> filter) {
        linkBefore(filter, tailEntry);
        return true;
    }

    @Override
    public void add(int index, Filter<? super T> filter) {
        // index == size 表示追加到尾部（AbstractSequentialList 语义）
        linkBefore(filter, index == size ? tailEntry : entryAt(index));
    }

    @Override
    public Filter<? super T> remove(int index) {
        Entry entry = entryAt(index);
        Filter<? super T> f = (Filter<? super T>) entry.filter;
        unlink(entry);
        return f;
    }

    @Override
    public Filter<? super T> set(int index, Filter<? super T> filter) {
        Entry entry = entryAt(index);
        Filter<? super T> old = (Filter<? super T>) entry.filter;
        // 替换：断开旧 filter 的连接，接入新 filter
        disconnect(entry.prev, entry);
        disconnect(entry, entry.next);
        old.setSource(null);
        entry.filter = (Filter<? super Object>) filter;
        filter.setSource((Source<?>) head);
        connect(entry.prev, entry);
        connect(entry, entry.next);
        old.getFilterIn().unlink();
        old.getFilterOut().unlinkAll();
        return old;
    }

    @Override
    public void clear() {
        for (Entry x = headEntry.next; x != tailEntry; ) {
            Entry next = x.next;
            x.filter.getFilterIn().unlink();
            x.filter.getFilterOut().unlinkAll();
            x.filter.setSource(null);
            x.filter = null;
            x.prev = null;
            x.next = null;
            x = next;
        }
        headEntry.next = tailEntry;
        tailEntry.prev = headEntry;
        size = 0;
    }

    /** 双向迭代器。 */
    private final class FilterListIterator implements ListIterator<Filter<? super T>> {
        private Entry lastReturned;
        private Entry next;
        private int nextIndex;

        FilterListIterator(int index) {
            next = (index == size) ? tailEntry : entryAt(index);
            nextIndex = index;
        }

        @Override
        public boolean hasNext() {
            return nextIndex < size;
        }

        @Override
        public Filter<? super T> next() {
            if (!hasNext()) throw new NoSuchElementException();
            lastReturned = next;
            next = next.next;
            nextIndex++;
            return (Filter<? super T>) lastReturned.filter;
        }

        @Override
        public boolean hasPrevious() {
            return nextIndex > 0;
        }

        @Override
        public Filter<? super T> previous() {
            if (!hasPrevious()) throw new NoSuchElementException();
            next = (next == null) ? tailEntry : next.prev;
            lastReturned = next;
            nextIndex--;
            return (Filter<? super T>) lastReturned.filter;
        }

        @Override
        public int nextIndex() {
            return nextIndex;
        }

        @Override
        public int previousIndex() {
            return nextIndex - 1;
        }

        @Override
        public void remove() {
            if (lastReturned == null) throw new IllegalStateException();
            Entry entry = lastReturned;
            lastReturned = null;
            unlink(entry);
            nextIndex--;
        }

        @Override
        public void set(Filter<? super T> filter) {
            if (lastReturned == null) throw new IllegalStateException();
            disconnect(lastReturned.prev, lastReturned);
            disconnect(lastReturned, lastReturned.next);
            lastReturned.filter = (Filter<? super Object>) filter;
            connect(lastReturned.prev, lastReturned);
            connect(lastReturned, lastReturned.next);
        }

        @Override
        public void add(Filter<? super T> filter) {
            lastReturned = null;
            linkBefore(filter, next);
            nextIndex++;
        }
    }
}