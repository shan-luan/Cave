package com.lomekwi.cave.pipeline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * 验证 {@link FilterList}：双向链表行为 + 端口连接自动维护。
 *
 * 覆盖：
 * 1) 空列表：Source.get() 无 filter 时返回源自身帧；
 * 2) add 后端口链 source.out → f.in → f.out → …；
 * 3) 按索引 add/remove 后连接保持；
 * 4) set 替换后连接更新；
 * 5) clear 后回到空链状态；
 * 6) listIterator 的 add/remove/set 维护连接；
 * 7) 求值：Source.get() 沿链传播。
 */
public class FilterListTest {

    /** 最小可测 Filter：把传入帧的 val 加 delta。 */
    static final class AddFilter extends Filter<Numable> {
        private final NumInPort delta = addInPort(new NumInPort("delta"));
        private final FilterIn in = addInPort(new FilterIn("in") {
        });
        private final FilterOut out = addOutPort(new FilterOut("out") {
            @Override
            public Numable getData() {
                Numable f = getFilterIn().getData();
                if (f != null) {
                    f.val += delta.getData().getVal();
                }
                return f;
            }
        });

        @Override
        public Class<Numable> getType() {
            return Numable.class;
        }

        @Override
        public String getName() {
            return "加数";
        }
    }

    /** 可复用帧：以 val 为内容。 */
    static final class Numable extends Frame {
        double val;

        Numable(double val) {
            super(null);
            this.val = val;
        }
    }

    static final class NumSrc extends Source<Numable> {
        private double base;

        NumSrc(double base) {
            super();
            this.base = base;
        }

        @Override
        protected Numable generate(long time, com.lomekwi.cave.timeline.Track track) {
            return new Numable(base);
        }

        @Override
        public void sync(long time, com.lomekwi.cave.timeline.Track track) {
        }

        @Override
        public long getLengthPerExportFrame() {
            return 1;
        }

        @Override
        public long getDuration() {
            return Long.MAX_VALUE;
        }

        @Override
        public String getDisplayName() {
            return "数字源";
        }

        @Override
        public Class<Numable> getFrameType() {
            return Numable.class;
        }

        @Override
        public com.lomekwi.cave.ui.editpanel.tlarea.SegActor createSegActor(
                com.lomekwi.cave.timeline.Segment segment) {
            return null;
        }
    }

    @Test
    public void empty_chain_returnsSourceFrame() {
        NumSrc src = new NumSrc(10);
        assertEquals(10.0, src.get(0, null).val, 0);
    }

    @Test
    public void add_linksPortsInOrder() {
        NumSrc src = new NumSrc(10);
        AddFilter f1 = new AddFilter();
        AddFilter f2 = new AddFilter();
        src.getFilters().add(f1);
        src.getFilters().add(f2);

        // source.out → f1.in
        assertSame(src.headOut, f1.getFilterIn().getPrev());
        // f1.out → f2.in
        assertSame(f1.getFilterOut(), f2.getFilterIn().getPrev());
        // 链末端：最后一个 filter 的 out 不连接
        assertFalse(f2.getFilterOut().isLinked());

        f1.delta.getDefaultData().setVal(1);
        f2.delta.getDefaultData().setVal(2);
        assertEquals(13.0, src.get(0, null).val, 0);
    }

    @Test
    public void addAtIndex_keepsChain() {
        NumSrc src = new NumSrc(10);
        AddFilter a = new AddFilter();
        AddFilter b = new AddFilter();
        AddFilter c = new AddFilter();
        src.getFilters().add(a);
        src.getFilters().add(c);
        src.getFilters().add(1, b);

        assertSame(src.headOut, a.getFilterIn().getPrev());
        assertSame(a.getFilterOut(), b.getFilterIn().getPrev());
        assertSame(b.getFilterOut(), c.getFilterIn().getPrev());
        assertFalse(c.getFilterOut().isLinked());

        a.delta.getDefaultData().setVal(1);
        b.delta.getDefaultData().setVal(2);
        c.delta.getDefaultData().setVal(3);
        assertEquals(16.0, src.get(0, null).val, 0);
    }

    @Test
    public void add_atSize_appendsToTail() {
        NumSrc src = new NumSrc(10);
        AddFilter a = new AddFilter();
        AddFilter b = new AddFilter();
        src.getFilters().add(a);
        // add(size) 是合法的追加语义，曾因 entryAt 越界而抛 IndexOutOfBounds
        src.getFilters().add(1, b);

        assertEquals(2, src.getFilters().size());
        assertEquals(a, src.getFilters().get(0));
        assertEquals(b, src.getFilters().get(1));
        assertSame(a.getFilterOut(), b.getFilterIn().getPrev());
    }

    @Test
    public void remove_rewiresNeighbors() {
        NumSrc src = new NumSrc(10);
        AddFilter a = new AddFilter();
        AddFilter b = new AddFilter();
        AddFilter c = new AddFilter();
        src.getFilters().add(a);
        src.getFilters().add(b);
        src.getFilters().add(c);
        src.getFilters().remove(b);

        assertEquals(2, src.getFilters().size());
        assertSame(src.headOut, a.getFilterIn().getPrev());
        assertSame(a.getFilterOut(), c.getFilterIn().getPrev());
        assertFalse(c.getFilterOut().isLinked());
        // b 被移除且解除挂载
        assertNull(b.getFilterIn().getPrev());
        assertFalse(b.getFilterOut().isLinked());
        assertNull(b.getSource());

        a.delta.getDefaultData().setVal(1);
        c.delta.getDefaultData().setVal(2);
        assertEquals(13.0, src.get(0, null).val, 0);

        // 链表顺序正确（regression：unlink 曾漏更新 pred.next/next.prev，导致残骸节点残留）
        assertEquals(a, src.getFilters().get(0));
        assertEquals(c, src.getFilters().get(1));
    }

    @Test
    public void set_replacesAndUnlinksOld() {
        NumSrc src = new NumSrc(10);
        AddFilter a = new AddFilter();
        AddFilter b = new AddFilter();
        src.getFilters().add(a);
        src.getFilters().add(b);

        AddFilter c = new AddFilter();
        src.getFilters().set(0, c);

        assertSame(src.headOut, c.getFilterIn().getPrev());
        assertSame(c.getFilterOut(), b.getFilterIn().getPrev());
        assertFalse(b.getFilterOut().isLinked());
        assertNull(a.getFilterIn().getPrev());
        assertFalse(a.getFilterOut().isLinked());
        assertNull(a.getSource());
        assertSame(src, c.getSource());

        c.delta.getDefaultData().setVal(5);
        b.delta.getDefaultData().setVal(1);
        assertEquals(16.0, src.get(0, null).val, 0);
    }

    @Test
    public void clear_returnsToEmptyChain() {
        NumSrc src = new NumSrc(10);
        AddFilter a = new AddFilter();
        AddFilter b = new AddFilter();
        src.getFilters().add(a);
        src.getFilters().add(b);
        src.getFilters().clear();

        assertEquals(0, src.getFilters().size());
        assertFalse(src.headOut.isLinked());
        assertNull(a.getSource());
        assertNull(b.getSource());
        assertEquals(10.0, src.get(0, null).val, 0);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void listIterator_add_remove_keepsChain() {
        NumSrc src = new NumSrc(10);
        AddFilter a = new AddFilter();
        AddFilter b = new AddFilter();
        List<Filter<Numable>> filters = (List) src.getFilters();

        // 先 add 两个
        filters.add(a);
        filters.add(b);

        // iterator add 到中间
        AddFilter mid = new AddFilter();
        var lit = filters.listIterator(1);
        lit.add(mid);

        assertEquals(3, filters.size());
        assertSame(src.headOut, a.getFilterIn().getPrev());
        assertSame(a.getFilterOut(), mid.getFilterIn().getPrev());
        assertSame(mid.getFilterOut(), b.getFilterIn().getPrev());
        assertFalse(b.getFilterOut().isLinked());

        // iterator remove 中间
        lit = filters.listIterator(1);
        assertEquals(mid, lit.next()); // 返回 mid
        lit.remove();
        assertEquals(2, filters.size());
        assertSame(a.getFilterOut(), b.getFilterIn().getPrev());
        assertNull(mid.getFilterIn().getPrev());
        assertFalse(mid.getFilterOut().isLinked());
        assertNull(mid.getSource());
    }

    @Test
    public void serialization_roundTrip_restoresChain() throws Exception {
        NumSrc src = new NumSrc(10);
        AddFilter f1 = new AddFilter();
        f1.delta.getDefaultData().setVal(3);
        src.getFilters().add(f1);

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(src);
        }
        NumSrc copy;
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bos.toByteArray()))) {
            copy = (NumSrc) ois.readObject();
        }

        assertEquals(1, copy.getFilters().size());
        assertSame(copy, copy.getFilters().get(0).getSource());
        // 端口连接恢复
        assertSame(copy.headOut, copy.getFilters().get(0).getFilterIn().getPrev());
        assertFalse(copy.getFilters().get(0).getFilterOut().isLinked());
        // 求值正确
        assertEquals(13.0, copy.get(0, null).val, 0);
    }
}