package com.lomekwi.cave.timeline;

import com.google.common.collect.Range;
import com.google.common.collect.RangeMap;
import com.google.common.collect.TreeRangeMap;
import com.google.common.eventbus.Subscribe;
import com.lomekwi.cave.pipeline.Frame;
import com.lomekwi.cave.pipeline.LastFrameEndEvent;
import com.lomekwi.cave.pipeline.NoFrameNowEvent;
import com.badlogic.gdx.Gdx;
import com.lomekwi.cave.timeline.playback.SeekEvent;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.Phaser;
import java.util.concurrent.locks.LockSupport;

import static java.util.Map.Entry;

@NullMarked
public class Track implements Serializable {
    public final int index;
    private final Timeline timeline;
    private transient RangeMap<Long, Segment> sources = TreeRangeMap.create();

    private long length;
    private boolean lengthChanged = true;
    private long @Nullable [] serializationRanges;
    private @Nullable List<Segment> serializationSources;
    private static final long serialVersionUID = 1L;
    private transient TrackWorker worker;

    public Track(Timeline timeline, int index) {
        this.timeline = timeline;
        this.index = index;
        this.worker = new TrackWorker();
    }

    synchronized protected void add(Segment segment, long start, long duration) {
        var r = Range.closedOpen(start, start + duration);
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
            var entry = sources.getEntry(time);
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
    synchronized public boolean isFree(Entry<Range<Long>, Segment> entry, Range<Long> range) {
        var m = sources.subRangeMap(range).asMapOfRanges();
        if (m.size() > 1) return false;
        if (m.isEmpty()) return true;
        return m.containsValue(entry.getValue());
    }

    synchronized protected void remove(long time) {
        Entry<Range<Long>, Segment> entry = sources.getEntry(time);
        if (entry != null) {
            sources.remove(entry.getKey());
        }
        lengthChanged = true;
    }

    synchronized protected void remove(Range<Long> range) {
        sources.remove(range);
        lengthChanged = true;
    }

    synchronized protected void resize(Entry<Range<Long>, Segment> e, long start, long duration) {
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
            var m =sources.subRangeMap(Range.atLeast(time)).asMapOfRanges();
            for(var entry:m.entrySet()){
                if(excludeHit&&entry.getKey().contains(time)) continue;
                return entry;
            }
        }else {
            var m =sources.subRangeMap(Range.atMost(time)).asDescendingMapOfRanges();
            for(var entry:m.entrySet()){
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
    synchronized public Set<Entry<Range<Long>, Segment>> getSubRangeMapAsEntrySet(Range<Long> range) {
        return Collections.unmodifiableSet(sources.subRangeMap(range).asMapOfRanges().entrySet());
    }

    private void writeObject(ObjectOutputStream oos) throws IOException {
        var ranges = sources.asMapOfRanges();
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
        worker = new TrackWorker();
    }

    public Timeline getTimeline() {
        return timeline;
    }

    public TrackWorker getWorker() {
        return worker;
    }

    @NullUnmarked
    public class TrackWorker implements Runnable {
        private final LastFrameEndEvent lastFrameEndEvent = new LastFrameEndEvent(Track.this);
        private final NoFrameNowEvent noFrameNowEvent = new NoFrameNowEvent(Track.this);
        private Phaser framePhaser;
        private Future<?> future;
        private volatile Thread workerThread;
        private volatile boolean sought;
        public TrackWorker() {
            timeline.project.projEventBus.register( this);
        }

        public Phaser getFramePhaser() {
            return framePhaser;
        }

        public Future<?> getFuture() {
            return future;
        }

        public void setFuture(Future<?> future) {
            this.future = future;
        }

        @Override
        public void run() {
            workerThread = Thread.currentThread();
            framePhaser = new Phaser(1);
            Gdx.app.log("Track", "轨道线程启动: " + Track.this);
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    long t=timeline.project.playhead.getTime();
                    var e = getEntry(t);
                    if(e == null){
                        timeline.project.projEventBus.post(noFrameNowEvent);
                        long parkTime = Long.MAX_VALUE;
                        var next = getEntry(t,1,false);
                        if(next!=null){
                            parkTime = next.getKey().lowerEndpoint()-t;
                            parkTime*=1000;//μs->ns
                            parkTime=Math.max(parkTime,1);
                        }
                        Gdx.app.debug("Track", "轨道线程等待: " + parkTime/1e9 + "秒");
                        LockSupport.parkNanos(parkTime);
                    }else{
                        Gdx.app.debug("Track", "找到片段: " + e.getValue());
                        long end = e.getKey().upperEndpoint();
                        while (t< end && !sought){
                            t=timeline.project.playhead.getTime();
                            Frame frame = get(t);
                            if (frame != null) {
                                timeline.project.projEventBus.post(lastFrameEndEvent);
                                timeline.project.projEventBus.post(frame);
                                framePhaser.arriveAndAwaitAdvance();
                            }
                        }
                    }
                    sought = false;
                }
            } catch (Exception e) {
                Gdx.app.error("Track", "在更新轨道时发生错误", e);
                Gdx.app.postRunnable(() -> {
                    throw e;
                });
            }finally {
                workerThread = null;
                framePhaser.forceTermination();
                framePhaser = null;
                Gdx.app.log("Track", "轨道线程结束: " + Track.this);
            }
        }
        @Subscribe
        public void onSeek(SeekEvent event){
            if(workerThread != null){
                LockSupport.unpark(workerThread);
            }
            sought = true;
        }
    }
}

