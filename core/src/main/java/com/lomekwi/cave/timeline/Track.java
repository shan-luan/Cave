package com.lomekwi.cave.timeline;

import com.google.common.collect.ImmutableRangeMap;
import com.google.common.collect.Range;
import com.google.common.collect.RangeMap;
import com.google.common.collect.TreeRangeMap;
import com.lomekwi.cave.pipeline.Frame;
import com.lomekwi.cave.pipeline.PipelineEvents;
import com.lomekwi.cave.app.Vars;
import com.badlogic.gdx.Gdx;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.Phaser;
import java.util.concurrent.locks.LockSupport;

@NullMarked
public class Track implements Serializable, Runnable {
    public final int index;
    private final Timeline timeline;
    private transient RangeMap<Long, Segment> sources = TreeRangeMap.create();

    private long length;
    private boolean lengthChanged = true;
    private long @Nullable [] serializationRanges;
    private @Nullable List<Segment> serializationSources;
    private static final long serialVersionUID = 1L;
    private transient PipelineEvents.LastFrameEndEvent lastFrameEndEvent = new PipelineEvents.LastFrameEndEvent(this);
    private transient PipelineEvents.NoFrameNowEvent noFrameNowEvent = new PipelineEvents.NoFrameNowEvent(this);
    private transient Phaser framePhaser = new Phaser(1);
    private @Nullable
    transient Future<?> future;

    public Track(Timeline timeline, int index) {
        this.timeline = timeline;
        this.index = index;
    }

    synchronized protected void add(Segment segment, long start, long duration) {
        Range<Long> r = Range.closedOpen(start, start + duration);
        sources.put(r, segment);
        segment.setTrack(this);
        segment.setRange(r);
        lengthChanged = true;
    }

    synchronized protected void remove(long start, long duration) {
        sources.remove(Range.closedOpen(start, start + duration));
        lengthChanged = true;
    }

    public @Nullable Frame get(long time) {
        Segment segment;
        synchronized (this) {
            Map.Entry<Range<Long>, Segment> entry = sources.getEntry(time);
            if (entry == null) {
                return null;
            }
            segment = entry.getValue();
        }
        return segment.get(time, this).withTrack(this.index);
    }

    /**
     * 检查指定范围是否与给定的条目兼容（即该范围是否为空闲或仅被同一片段占用）
     *
     * @param entry 要检查的片段条目，包含时间范围和对应的Segment对象
     * @param range 要检查的时间范围
     * @return 如果范围内没有其他片段占用则返回true；如果范围内只有与entry相同的片段也返回true；否则返回false
     */
    synchronized public boolean isFree(Map.Entry<Range<Long>, Segment> entry, Range<Long> range) {
        Map<Range<Long>, Segment> m = sources.subRangeMap(range).asMapOfRanges();
        if (m.size() > 1) return false;
        if (m.isEmpty()) return true;
        return m.containsValue(entry.getValue());
    }

    synchronized protected void remove(long time) {
        Map.Entry<Range<Long>, Segment> entry = sources.getEntry(time);
        if (entry != null) {
            sources.remove(entry.getKey());
        }
        lengthChanged = true;
    }

    synchronized protected void remove(Range<Long> range) {
        sources.remove(range);
        lengthChanged = true;
    }

    synchronized protected void resize(Map.Entry<Range<Long>, Segment> e, long start, long duration) {
        remove(e.getKey());
        add(e.getValue(), start, duration);
    }

    synchronized public Map.@Nullable Entry<Range<Long>, Segment> getEntry(long time) {
        return sources.getEntry(time);
    }
    /**
     * 获取指定时间点的片段条目，支持偏移查找
     *
     * @param time      查询的时间点
     * @param offset    偏移量，0表示精确匹配时间点；正数表示查找该时间之后的第一个片段；负数表示查找该时间之前的最后一个片段。建议只使用-1,0,1，防止接口变动。
     * @param excludeHit 是否排除命中时间点的片段本身。true表示跳过包含time的片段，false表示可以返回包含time的片段
     * @return 找到的片段条目，如果未找到则返回null
     */
    synchronized public Map.@Nullable Entry<Range<Long>, Segment> getEntry(long time,int offset,boolean excludeHit) {
        if(offset==0){
            if(excludeHit){
                return null;//为什么会有人使用这个参数组合啊喂
            }else {
                return sources.getEntry(time);
            }
        } else if (offset > 0) {
            Map<Range<Long>, Segment> m =sources.subRangeMap(Range.atLeast(time)).asMapOfRanges();
            for(Map.Entry<Range<Long>, Segment> entry:m.entrySet()){
                if(excludeHit&&entry.getKey().contains(time)) continue;
                return entry;
            }
        }else {
            Map<Range<Long>, Segment> m =sources.subRangeMap(Range.atMost(time)).asDescendingMapOfRanges();
            for(Map.Entry<Range<Long>, Segment> entry:m.entrySet()){
                if(excludeHit&&entry.getKey().contains(time)) continue;
                return entry;
            }
        }
        return null;
    }

    synchronized public long getLength() {
        if (lengthChanged) {
            if (sources.asMapOfRanges().isEmpty()) {
                length = 0;
            } else {
                length = sources.span().upperEndpoint();
            }
            lengthChanged = false;
        }
        return length;
    }
    synchronized public RangeMap<Long, Segment> getSubRangeMapSnapshot(Range<Long> range) {
        return ImmutableRangeMap.copyOf(sources.subRangeMap(range));
    }

    private void writeObject(ObjectOutputStream oos) throws IOException {
        Map<Range<Long>, Segment> ranges = sources.asMapOfRanges();
        serializationRanges = new long[ranges.size() * 2];
        serializationSources = new ArrayList<>(ranges.values());
        int i = 0;
        for (Range<Long> r : ranges.keySet()) {
            serializationRanges[i] = r.lowerEndpoint();
            serializationRanges[i + 1] = r.upperEndpoint();
            i += 2;
        }
        oos.defaultWriteObject();
        serializationRanges = null;
        serializationSources = null;
    }

    private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
        ois.defaultReadObject();
        sources = TreeRangeMap.create();
        for (int i = 0; i < serializationRanges.length; i += 2) {
            sources.put(Range.closedOpen(serializationRanges[i], serializationRanges[i + 1]), serializationSources.get(i / 2));
        }
        serializationRanges = null;
        serializationSources = null;

        framePhaser = new Phaser(1);

        lastFrameEndEvent = new PipelineEvents.LastFrameEndEvent(this);
        noFrameNowEvent = new PipelineEvents.NoFrameNowEvent(this);
    }

    public Phaser getFramePhaser() {
        return framePhaser;
    }

    public Timeline getTimeline() {
        return timeline;
    }

    public @Nullable Future<?> getFuture() {
        return future;
    }

    public void setFuture(@Nullable Future<?> future) {
        this.future = future;
    }

    @Override
    public void run() {
        Gdx.app.log("Track", "轨道线程启动: " + this);
        try {
            while (!Thread.currentThread().isInterrupted()) {
                Frame frame = get(timeline.getProject().playhead.getTime());
                if (frame != null) {
                    timeline.getProject().projEventBus.post(lastFrameEndEvent);
                    timeline.getProject().projEventBus.post(frame);
                    framePhaser.arriveAndAwaitAdvance();
                } else {
                    timeline.getProject().projEventBus.post(noFrameNowEvent);
                    LockSupport.parkNanos(1000000L);
                }
            }
        } catch (Exception e) {
            Gdx.app.error("Track", "在更新轨道时发生错误", e);
            Gdx.app.postRunnable(() -> {
                throw e;
            });
        }finally {
            Gdx.app.log("Track", "轨道线程结束: " + this);
        }
    }
}

