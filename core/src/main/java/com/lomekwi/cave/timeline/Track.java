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

import static com.lomekwi.cave.util.Ranges.shift;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
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
import static java.lang.Math.*;
import static com.google.common.primitives.Longs.*;

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

    /**
     * 尝试在轨道中加入一个片段。仅当可加入时才会被真的加入。
     * @return 如果目标区间被占用而不能添加，将目标区间偏移多少时间才可以添加。返回0时不需要偏移即成功添加。本类的其他返回long的方法大多也是此语义。
     */
    synchronized protected long tryAdd(Segment segment,Range<Long> r) {
        var shift=getShift(r);
        if(shift==0) {
            override(segment,r);
        }
        return shift;
    }
    synchronized protected long getShift(Range<Long> r){
        return getShift(r, (Range<Long>) null);
    }
    synchronized protected long getShift(Range<Long> r,@Nullable Range<Long> exclude){
        return pickShift(getShiftForward(r, exclude), getShiftBackward(r, exclude));
    }
    synchronized protected long getShift(Range<Long> r,Collection<Segment> ignore){
        return pickShift(getShiftForward(r, ignore), getShiftBackward(r, ignore));
    }
    synchronized protected long getShiftForward(Range<Long> r,@Nullable Range<Long> exclude){
        return shiftScan(r, exclude, List.of(), true);
    }
    synchronized protected long getShiftForward(Range<Long> r,Collection<Segment> ignore){
        return shiftScan(r, null, ignore, true);
    }
    synchronized protected long getShiftBackward(Range<Long> r,@Nullable Range<Long> exclude){
        return shiftScan(r, exclude, List.of(), false);
    }
    synchronized protected long getShiftBackward(Range<Long> r,Collection<Segment> ignore){
        return shiftScan(r, null, ignore, false);
    }

    private synchronized long pickShift(long forward, long backward){
        boolean fOk = forward != Long.MAX_VALUE;
        boolean bOk = backward != Long.MIN_VALUE;
        if (fOk && bOk) {
            return Math.abs(forward) <= Math.abs(backward) ? forward : backward;
        }
        if (fOk) return forward;
        if (bOk) return backward;
        return 0;
    }

    private synchronized long shiftScan(Range<Long> r,@Nullable Range<Long> exclude,Collection<Segment> ignore,boolean forward){
        long lo = r.lowerEndpoint();
        long hi = r.upperEndpoint();
        long s = 0;
        for (int step = 0; step < MAX_SLIDE_STEPS; step++) {
            if (freeAt(shift(r, s), exclude, ignore)) {
                return s;
            }
            if (forward) {
                long maxEnd = Long.MIN_VALUE;
                boolean found = false;
                for (var e : sources.subRangeMap(shift(r, s)).asMapOfRanges().entrySet()) {
                    if (exclude != null && e.getKey().isConnected(exclude)) continue;
                    if (ignore.contains(e.getValue())) continue;
                    found = true;
                    maxEnd = Math.max(maxEnd, e.getKey().upperEndpoint());
                }
                if (!found) return s;
                long next = maxEnd - lo;
                if (next <= s) return Long.MAX_VALUE;
                s = next;
            } else {
                long minStart = Long.MAX_VALUE;
                boolean found = false;
                for (var e : sources.subRangeMap(shift(r, s)).asMapOfRanges().entrySet()) {
                    if (exclude != null && e.getKey().isConnected(exclude)) continue;
                    if (ignore.contains(e.getValue())) continue;
                    found = true;
                    minStart = Math.min(minStart, e.getKey().lowerEndpoint());
                }
                if (!found) return s;
                long next = minStart - hi;
                if (next >= s) return s;
                s = next;
            }
        }
        return s;
    }

    private synchronized boolean freeAt(Range<Long> range,@Nullable Range<Long> exclude,Collection<Segment> ignore){
        for (var e : sources.subRangeMap(range).asMapOfRanges().entrySet()) {
            if (exclude != null && e.getKey().isConnected(exclude)) continue;
            if (ignore.contains(e.getValue())) continue;
            return false;
        }
        return true;
    }
    private static final int MAX_SLIDE_STEPS = 10000;

    /**
     *只是不检查。千万不要真的拿来覆盖。
     * @author shan_luan_
     */
    synchronized protected void override(Segment segment,Range<Long> r){
        assert isFree(r,Collections.singleton(segment));
        sources.put(r, segment);
        segment.setTrack(this);
        segment.setRange(r);
        onChanged();
    }
    synchronized protected boolean remove(Segment segment){
        var r = segment.getRange();
        if(r != null){
            sources.remove(r);
            return true;
        }else {
            return false;
        }
    }
    synchronized protected void remove(Collection<Segment> segments){
        for(var s : segments){
            remove(s);
        }
    }

    /**
     * 检查指定时间范围是否空闲（忽略指定片段集合中的片段）
     *
     * @param range  要检查的时间范围
     * @param ignore 不视为障碍的片段集合（为空时相当于完全空闲检查）
     * @return 如果范围内没有任何非忽略片段占用则返回 true
     */
    synchronized public boolean isFree(Range<Long> range, Collection<Segment> ignore) {
        var m = sources.subRangeMap(range).asMapOfRanges();
        if (m.isEmpty()) return true;
        if (ignore.isEmpty()) return false;
        for (var entry : m.entrySet()) {
            if (!ignore.contains(entry.getValue())) return false;
        }
        return true;
    }

    synchronized protected boolean split(long time){
        var s = sources.get(time);
        if (s == null) return false;
        var r = s.getRange();
        long lo = r.lowerEndpoint();
        long hi = r.upperEndpoint();
        if (time <= lo || time >= hi) return false;
        var right = s.duplicate();
        sources.remove(r);
        override(s, Range.closedOpen(lo, time));
        override(right, Range.closedOpen(time, hi));
        return true;
    }

    /**
     * @author shan_luan_
     */
    synchronized protected long probeSetStart(Collection<Segment> segments,long deltaTime){
        if(deltaTime==0) return 0;
        var f = segments.stream().filter(s -> s.getTrack()==this);
        if(deltaTime>0){
            return f.mapToLong(s -> max(0,s.getRange().lowerEndpoint()+deltaTime-s.getRange().upperEndpoint())).min().orElse(-deltaTime);
        }else {
            return f.mapToLong(s -> {
                var newStart = s.getRange().lowerEndpoint()+deltaTime;
                return -min(
                    newStart-s.prevRange().upperEndpoint(),
                    newStart-s.getMinStart(),
                    0
                );
            }).max().orElse(-deltaTime);
        }
    }
//ai shit impl.
private List<Segment> own(Collection<Segment> segments){
    var out = new ArrayList<Segment>(segments.size());
    for(var s : segments){
        if(s.getTrack()==this) out.add(s);
    }
    return out;
}
    synchronized protected void setStart(Collection<Segment> segments,long deltaTime){
        segments.stream().filter(s->s.getTrack()==this).forEach(s -> setStart(s,deltaTime));
    }
    synchronized protected void setStart(Segment segment,long deltaTime){
        remove(segment);
        override(segment,Range.closedOpen(segment.getRange().lowerEndpoint()+deltaTime,segment.getRange().upperEndpoint()));
    }

    /**
     * @author shan_luan_
     */
    synchronized protected long probeSetEnd(Collection<Segment> segments,long deltaTime){
        if(deltaTime==0) return 0;
        var f = segments.stream().filter(s -> s.getTrack()==this);
        if(deltaTime>0){
            return f.mapToLong(s -> {
                var newEnd = s.getRange().upperEndpoint()+deltaTime;
                return min(
                    s.nextRange().lowerEndpoint()-newEnd
                    ,s.getMaxEnd()-newEnd
                    ,0
                );
            }).min().orElse(-deltaTime);
        }else {
            return f.mapToLong(s -> max(0,s.getRange().lowerEndpoint()-s.getRange().upperEndpoint()-deltaTime)).min().orElse(-deltaTime);
        }
    }
    synchronized protected void setEnd(Collection<Segment> segments,long deltaTime){
        segments.stream().filter(s->s.getTrack()==this).forEach(s -> setEnd(s,deltaTime));
    }
    synchronized protected void setEnd(Segment segment,long deltaTime){
        remove(segment);
        override(segment,Range.closedOpen(segment.getRange().lowerEndpoint(),segment.getRange().upperEndpoint()+deltaTime));
    }

    synchronized protected long probeMove(Collection<Segment> segments,long deltaTime){
        segments = own(segments);
        long max=0;
        for(var s : segments){
            var r = s.getRange();
            var d = getShift(shift(r,deltaTime),r);
            max=Math.abs(d)>Math.abs(max)?d : max;
        }
        return max;
    }

    synchronized protected void move(Collection<Segment> segments,long deltaTime){
        segments = own(segments);
        remove(segments);
        for(var s : segments){
            override(s,shift(s.getRange(),deltaTime));
            s.offsetOrigin(deltaTime);
        }
    }

    synchronized public @Nullable Segment get(long time) {
        return sources.get(time);
    }
    /**
     * 获取指定时间点的片段，支持偏移查找
     *
     * @param time      查询的时间点
     * @param offset    偏移量，0表示精确匹配时间点；正数表示查找该时间之后的第一个片段；负数表示查找该时间之前的最后一个片段。建议只使用-1,0,1，防止接口变动。
     * @param excludeHit 是否排除命中时间点的片段本身。true表示跳过包含time的片段，false表示可以返回包含time的片段
     * @return 找到的片段，如果未找到则返回null
     */
    synchronized public @Nullable Segment get(long time, int offset, boolean excludeHit) {
        if(offset==0){
            if(excludeHit){
                return null;
            }else {
                return sources.get(time);
            }
        } else if (offset > 0) {
            var m =sources.subRangeMap(Range.atLeast(time)).asMapOfRanges();
            for(var entry:m.entrySet()){
                if(excludeHit&&entry.getKey().contains(time)) continue;
                return entry.getValue();
            }
        }else {
            var m =sources.subRangeMap(Range.atMost(time)).asDescendingMapOfRanges();
            for(var entry:m.entrySet()){
                if(excludeHit&&entry.getKey().contains(time)) continue;
                return entry.getValue();
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

    /**
     * 两条轨道相等，当且仅当轨道索引相同、片段区间集合逐项相同。
     * RangeMap 的 entrySet 迭代顺序总是按区间从小到大，因此两侧可以逐项对齐比较。
     * 片段本身不做身份比较，而是逐字段比较（源类型/时长、origin、区间），
     * 以便跨时间线（如序列化快照）的结构对比。
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Track other)) return false;
        if (index != other.index) return false;
        var a = sources.asMapOfRanges().entrySet();
        var b = other.sources.asMapOfRanges().entrySet();
        if (a.size() != b.size()) return false;
        Iterator<Entry<Range<Long>, Segment>> ia = a.iterator();
        Iterator<Entry<Range<Long>, Segment>> ib = b.iterator();
        while (ia.hasNext()) {
            var ea = ia.next();
            var eb = ib.next();
            if (!ea.getKey().equals(eb.getKey())) return false;
            if (!segmentEquals(ea.getValue(), eb.getValue())) return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(index);
    }

    private static boolean segmentEquals(Segment a, Segment b) {
        if (a == b) return true;
        var sa = a.getSource();
        var sb = b.getSource();
        return sa.getClass() == sb.getClass()
            && sa.getDuration() == sb.getDuration()
            && a.getOrigin() == b.getOrigin()
            && Objects.equals(a.getRange(), b.getRange());
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

                        var s = get(t);
                        Frame f = null;
                        if (s != null) {
                            s.sync(t);
                            f = s.get(t);
                        }
                        timeline.project.projEventBus.post(Objects.requireNonNullElse(f, gapFrame));

                        LockSupport.park();
                        continue;
                    }else {
                        updateNeeded = false;
                    }
                    var s = get(t);
                    if(s == null){
                        timeline.project.projEventBus.post(gapFrame);
                        long parkTime = Long.MAX_VALUE;
                        var next = get(t,1,false);
                        if(next!=null){
                            parkTime = next.getRange().lowerEndpoint()-t;
                            parkTime*=1000;
                            parkTime=Math.max(parkTime,1);
                        }
                        Gdx.app.debug("Track"+index, "轨道线程等待: " + parkTime/1e9 + "秒");
                        LockSupport.parkNanos(parkTime);
                    }else{
                        var r = s.getRange();
                        Gdx.app.debug("Track"+index, "找到片段: " + s);
                        s.sync(t);
                        long end = r.upperEndpoint();
                        while (t< end && !updateNeeded && !Thread.currentThread().isInterrupted()){
                            t=timeline.project.playhead.getTime();
                            Frame frame = s.get(t);
                            if(updateNeeded) break;
                            if (frame != null) {
                                timeline.project.projEventBus.post(frame);
                                int phase = sinkPhaser.arrive();
                                try {
                                    sinkPhaser.awaitAdvanceInterruptibly(phase);
                                } catch (InterruptedException ie) {
                                    Thread.currentThread().interrupt();
                                }
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

