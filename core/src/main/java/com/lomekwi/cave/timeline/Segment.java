package com.lomekwi.cave.timeline;

import com.google.common.collect.Range;
import com.lomekwi.cave.app.copy.Copyable;
import com.lomekwi.cave.app.selection.Selectable;
import com.lomekwi.cave.pipeline.Frame;
import com.lomekwi.cave.pipeline.Source;
import com.lomekwi.cave.ui.editpanel.tlarea.SegActor;
import com.lomekwi.cave.util.Duplicatable;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.io.Serializable;
import java.util.Iterator;

@NullMarked
public class Segment implements Serializable, Iterable<Frame>, Duplicatable<Segment>, Selectable, Copyable, Comparable<Segment> {
    @Serial
    private static final long serialVersionUID = 1L;
    private final Source<?> source;

    protected Source<?> source() {
        return source;
    }
    public Source<?> getSource() {
        return source;
    }
    private transient @Nullable Track track;
    private transient @Nullable SegActor actor;
    private transient @Nullable Range<Long> range;
    private transient boolean selected;
    private @Nullable SegmentGroup group;
    public boolean isSelected() {
        return selected;
    }
    public void setSelected(boolean selected) {
        this.selected = selected;
    }
    public @Nullable SegmentGroup getGroup() {
        return group;
    }
    public void setGroup(@Nullable SegmentGroup group) {
        this.group = group;
    }
    /**
     * 源的0秒在时间轴中的位置
     */
    private volatile long origin;
    public long getOrigin() {
        return origin;
    }
    public void setOrigin(long origin) {
        this.origin = origin;
    }
    @SuppressWarnings("NonAtomicOperationOnVolatileField")//单写多读
    public void offsetOrigin(long offset) {
        this.origin += offset;
    }
    public Segment(Source<?> source) {
        this.source = source;
        source.setSegment(this);
        actor = source.createSegActor(this);
    }
    protected void setTrack(@Nullable Track track) {
        this.track = track;
    }

    /**
     * @param time 绝对时间
     */
    public @Nullable Frame get(long time) {
        var f = source.get(toLocalTime(time), track);
        if (f == null) return null;
        return f.withTime(time);
    }

    /**
     * 同步到指定时间
     * @param time 绝对时间
     */
    public void sync(long time) throws Exception {
        source.sync(toLocalTime(time), track);
    }
    public SegActor getActor() {
        return java.util.Objects.requireNonNull(actor, "Segment actor is not initialized");
    }
    public long toLocalTime(long time) {
        return time - origin;
    }
    /** 该片段对应的媒体源总时长（微秒） */
    public long getDuration() {
        return source.getDuration();
    }
    public @Nullable Track getTrack(){
        return track;
    }

    public @Nullable Range<Long> getRange() {
        return range;
    }
    /**获取拉伸时在时间轴上合法的最小起点*/
    public long getMinStart(){
        return Math.max(0,origin);
    }
    /**获取拉伸时在时间轴上合法的最大终点*/
    public long getMaxEnd(){
        long duration = source.getDuration();
        if (duration == Long.MAX_VALUE) return Long.MAX_VALUE;
        return origin + duration;
    }

    /**
     * 按轨道索引、再按片段起始时间排序（轨道越小越靠前，起始时间越小越靠前）
     */
    @Override
    public int compareTo(Segment o) {
        int c = Integer.compare(trackIndex(), o.trackIndex());
        if (c != 0) return c;
        return Long.compare(rangeStart(), o.rangeStart());
    }

    private int trackIndex() {
        var track = this.track;
        return track == null ? Integer.MAX_VALUE : track.index;
    }

    private long rangeStart() {
        var range = this.range;
        return range == null ? Long.MAX_VALUE : range.lowerEndpoint();
    }
    protected void setRange(Range<Long> range) {
        if(range.equals(this.range))return;
        this.range = range;
    }

    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        source.setSegment(this);
        actor = source.createSegActor(this);
    }
    @Override
    public Iterator<Frame> iterator() {
        return new IteratorImpl();
    }
    public class IteratorImpl implements Iterator<Frame> {
        private long time = requireRange().lowerEndpoint() % source.getLengthPerExportFrame();
        @Override
        public boolean hasNext() {
            return time <= requireRange().upperEndpoint();
        }
        @Override
        public Frame next() {
            Frame f = java.util.Objects.requireNonNull(get(time), "Segment frame is unavailable");
            time += source.getLengthPerExportFrame();
            return f;
        }
    }
    @Override
    public Copyable copy() {
        return duplicate();
    }

    @Override
    public Segment duplicate() {
        var savedGroup = group;
        group = null;
        var segment =  Duplicatable.super.duplicate();
        group = savedGroup;
        segment.range = range;
        segment.source.onDuplicate(source);
        return segment;
    }
    /**
     * @author shan_luan_
     */
    public @Nullable Segment next(){
        var track = requireTrack();
        var range = requireRange();
        var e = track.getSubRangeMapAsEntrySet(Range.atLeast(range.upperEndpoint()));
        for(var next : e){
            return next.getValue();
        }
        return null;
    }

    /**
     * @author shan_luan_
     */
    public @Nullable Segment prev(){
        var track = requireTrack();
        var range = requireRange();
        return track.get(range.lowerEndpoint(), -1, true);
    }
    public Range<Long> nextRange(){
        var next = next();
        if(next != null){
            return next.requireRange();
        }else {
            return Range.singleton(Long.MAX_VALUE);
        }
    }
    public Range<Long> prevRange(){
        var prev = prev();
        if(prev != null){
            return prev.requireRange();
        }else {
            return Range.singleton(0L);
        }
    }

    private Track requireTrack() {
        return java.util.Objects.requireNonNull(track, "Segment is not attached to a track");
    }

    private Range<Long> requireRange() {
        return java.util.Objects.requireNonNull(range, "Segment has no timeline range");
    }
}
