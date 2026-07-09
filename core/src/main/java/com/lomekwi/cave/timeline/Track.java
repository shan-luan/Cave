package com.lomekwi.cave.timeline;

import com.google.common.collect.Range;
import com.google.common.collect.RangeMap;
import com.google.common.collect.TreeRangeMap;
import com.google.common.eventbus.Subscribe;
import com.lomekwi.cave.pipeline.Frame;
import com.lomekwi.cave.pipeline.GapFrame;
import com.badlogic.gdx.Gdx;
import com.lomekwi.cave.timeline.playback.PlayStateChangedEvent;
import com.lomekwi.cave.timeline.playback.Playhead;
import com.lomekwi.cave.timeline.playback.RefreshRequestEvent;
import com.lomekwi.cave.timeline.playback.SeekEvent;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.Phaser;
import java.util.concurrent.locks.LockSupport;

import static java.util.Map.Entry;

@NullMarked
public class Track implements Serializable,Iterable<Segment> {
    public final int index;
    private transient Timeline timeline;
    private transient RangeMap<Long, Segment> sources = TreeRangeMap.create();

    private long length;
    private boolean lengthChanged = true;
    private long @Nullable [] serializationRanges;
    private @Nullable List<Segment> serializationSources;
    @Serial
    private static final long serialVersionUID = 1L;
    private transient @Nullable TrackWorker worker;

    public Track(Timeline timeline, int index) {
        this.timeline = timeline;
        this.index = index;
        this.worker = new TrackWorker();
    }

    void setTimeline(Timeline timeline) {
        this.timeline = timeline;
    }

    synchronized protected boolean isEmpty() {
        return sources.asMapOfRanges().isEmpty();
    }

    synchronized protected void add(Segment segment, long start, long duration) {
        var r = Range.closedOpen(start, start + duration);
        sources.put(r, segment);
        segment.setTrack(this);
        segment.setRange(r);
        onChanged();
    }

    synchronized protected void remove(long start, long duration) {
        sources.remove(Range.closedOpen(start, start + duration));
        onChanged();
    }
    synchronized protected void remove(Range<Long> range) {
        sources.remove(range);
        onChanged();
    }
    synchronized protected void remove(long time) {
        var entry = sources.getEntry(time);
        if (entry != null) {
            sources.remove(entry.getKey());
        }
        onChanged();
    }

    /**
     * 检查指定时间范围是否空闲（忽略指定片段集合中的片段）
     *
     * @param range  要检查的时间范围
     * @param ignore 不视为障碍的片段集合（为空时相当于完全空闲检查）
     * @return 如果范围内没有任何非忽略片段占用则返回 true
     */
    synchronized public boolean isFree(Range<Long> range, Set<Segment> ignore) {
        var m = sources.subRangeMap(range).asMapOfRanges();
        if (m.isEmpty()) return true;
        if (ignore.isEmpty()) return false;
        for (var entry : m.entrySet()) {
            if (!ignore.contains(entry.getValue())) return false;
        }
        return true;
    }

    synchronized protected void split(long time){
        var entry = sources.getEntry(time);
        if (entry == null) return;
        var s = entry.getValue();
        long start = entry.getKey().lowerEndpoint();
        long duration = entry.getKey().upperEndpoint() - start;
        long offset = time - start;
        var ns = s.duplicate();
        sources.remove(Range.closedOpen(start, start + duration));
        add(s, start, offset);
        add(ns, time, duration - offset);
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
    private void onChanged() {
        lengthChanged = true;
        if(worker != null){
            worker.onTrackChanged();
        }
    }

    @Serial
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

    @Serial
    private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
        ois.defaultReadObject();
        sources = TreeRangeMap.create();
        if (serializationRanges == null || serializationSources == null) {
            Gdx.app.error("Track", "Track 序列化数据为 null");
        } else {
            for (int i = 0; i < serializationRanges.length; i += 2) {
                var r =Range.closedOpen(serializationRanges[i], serializationRanges[i + 1]);
                var s =serializationSources.get(i / 2);
                sources.put(r, s);
                s.setRange(r);
                s.setTrack(this);
            }
            serializationRanges = null;
            serializationSources = null;
        }
    }

    public Timeline getTimeline() {
        return timeline;
    }

    public TrackWorker getWorker() {
        if(worker==null){
            worker = new TrackWorker();
        }
        return worker;
    }

    @Override
    public Iterator<Segment> iterator() {
        return sources.asMapOfRanges().values().iterator();
    }

    @NullUnmarked
    public class TrackWorker implements Runnable {
        private final GapFrame gapFrame = new GapFrame(Track.this);
        private Phaser sinkPhaser;
        private Future<?> future;
        private volatile Thread workerThread;
        private volatile boolean updateNeeded;
        public TrackWorker() {
            timeline.project.projEventBus.register( this);
        }

        public Phaser getSinkPhaser() {
            return sinkPhaser;
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
            sinkPhaser = new Phaser(1);
            Gdx.app.log("Track"+index, "轨道线程启动: " + Track.this);
            try {
                Playhead p=timeline.project.playhead;
                while (!Thread.currentThread().isInterrupted()) {
                    long t=p.getTime();
                    if(!p.isPlaying()){
                        Gdx.app.debug("Track"+index, "因为播放头而尝试park...");

                        //用于暂停时seek了或修改了轨道
                        var s = getEntry(t);
                        Frame f = null;
                        if (s != null) {
                            s.getValue().sync(t);
                            f = s.getValue().get(t);
                        }
                        timeline.project.projEventBus.post(Objects.requireNonNullElse(f, gapFrame));

                        LockSupport.park();
                        continue;//防止之前持有许可,一次park不够
                    }else {
                        updateNeeded = false;
                    }
                    var e = getEntry(t);
                    if(e == null){
                        timeline.project.projEventBus.post(gapFrame);
                        long parkTime = Long.MAX_VALUE;
                        var next = getEntry(t,1,false);
                        if(next!=null){
                            parkTime = next.getKey().lowerEndpoint()-t;
                            parkTime*=1000;//μs->ns
                            parkTime=Math.max(parkTime,1);
                        }
                        Gdx.app.debug("Track"+index, "轨道线程等待: " + parkTime/1e9 + "秒");
                        LockSupport.parkNanos(parkTime);
                    }else{
                        var s=e.getValue();
                        var r=e.getKey();
                        Gdx.app.debug("Track"+index, "找到片段: " + s);
                        s.sync(t);
                        long end = r.upperEndpoint();
                        while (t< end && !updateNeeded){
                            t=timeline.project.playhead.getTime();
                            Frame frame = s.get(t);
                            if(updateNeeded) break;
                            if (frame != null) {
                                timeline.project.projEventBus.post(frame);
                                sinkPhaser.arriveAndAwaitAdvance();
                            }
                        }
                    }
                }
            } catch (Exception e) {
                if(!(e instanceof InterruptedException)) {
                    Gdx.app.error("Track" + index, "在更新轨道时发生错误", e);
                    Gdx.app.postRunnable(() -> {
                        throw new RuntimeException(e);
                    });
                }
            }finally {
                workerThread = null;
                Gdx.app.log("Track"+index, "轨道线程结束: " + Track.this);
            }
        }
        @Subscribe
        public void onPlayStateChanged(PlayStateChangedEvent event){
            update();
        }
        @Subscribe
        public void onRefreshRequested(RefreshRequestEvent event){
            update();
        }
        @Subscribe
        public void onSeek(SeekEvent event){
            update();
        }
        protected void onTrackChanged(){
            update();
        }
        private void update(){
            var t = workerThread;
            if(t != null){
                LockSupport.unpark(t);
            }
            updateNeeded = true;
        }
    }
}

