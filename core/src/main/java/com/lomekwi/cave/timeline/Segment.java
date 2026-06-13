package com.lomekwi.cave.timeline;

import com.google.common.collect.Range;
import com.lomekwi.cave.pipeline.Frame;
import com.lomekwi.cave.pipeline.Source;
import com.lomekwi.cave.ui.editpanel.tlarea.SegActor;
import com.lomekwi.cave.util.Duplicatable;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.io.Serializable;
import java.util.AbstractMap;
import java.util.Iterator;

public abstract class Segment implements Serializable,Iterable<Frame>, Duplicatable<Segment> {
    @Serial
    private static final long serialVersionUID = 1L;
    private final Source<?> source;
    private Track track;
    private transient SegActor actor;
    private transient Range<Long> range;
    private AbstractMap.SimpleImmutableEntry<Range<Long>, Segment> entry;
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
    protected Segment(Source<?> source) {
        this.source = source;
        actor = setupActor();
    }
    protected void setTrack(Track track) {
        this.track = track;
    }

    /**
     * @param time 绝对时间
     */
    public Frame get(long time) {
        return source.get(toLocalTime(time), track).withTrack(track.index).withTime(time);
    }

    /**
     * 同步到指定时间
     * @param time 绝对时间
     */
    public void sync(long time) throws Exception {
        source.sync(toLocalTime(time), track);
    }
    public SegActor getActor() {
        return actor;
    }
    public long toLocalTime(long time) {
        return time - origin;
    }
    /** 该片段对应的媒体源总时长（微秒） */
    public long getDuration() {
        return source.getDuration();
    }
    public Track getTrack(){
        return track;
    }

    public Range<Long> getRange() {
        return range;
    }
    public AbstractMap.Entry<Range<Long>, Segment> getEntry() {
        return entry;
    }
    protected void setRange(Range<Long> range) {
        if(range.equals(this.range))return;
        this.range = range;
        entry = new AbstractMap.SimpleImmutableEntry<>(range,this);
    }
    protected abstract SegActor setupActor();

    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        actor = setupActor();
    }
    @Override
    public Iterator<Frame> iterator() {
        return new IteratorImpl();
    }
    public class IteratorImpl implements Iterator<Frame> {
        private long time = range.lowerEndpoint() % source.getLengthPerExportFrame();
        @Override
        public boolean hasNext() {
            return time <= range.upperEndpoint();
        }
        @Override
        public Frame next() {
            Frame f = get(time);
            time += source.getLengthPerExportFrame();
            return f;
        }
    }
    @Override
    public Segment duplicate() {
        var segment =  Duplicatable.super.duplicate();
        segment.source.onDuplicate(source);
        return segment;
    }
}
