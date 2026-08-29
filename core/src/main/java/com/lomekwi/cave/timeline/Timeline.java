package com.lomekwi.cave.timeline;

import com.google.common.collect.Range;
import com.lomekwi.cave.project.Project;
import com.lomekwi.cave.timeline.UndoManager.AddSegCommand;
import com.lomekwi.cave.timeline.UndoManager.CompoundCommand;
import com.lomekwi.cave.timeline.UndoManager.MergeableCommand;
import com.lomekwi.cave.timeline.UndoManager.MoveSegsCommand;
import com.lomekwi.cave.timeline.UndoManager.RemoveSegCommand;
import com.lomekwi.cave.timeline.UndoManager.RemoveSegsCommand;
import com.lomekwi.cave.timeline.UndoManager.ResizeSegsCommand;
import com.lomekwi.cave.timeline.UndoManager.SplitSegCommand;
import com.lomekwi.cave.timeline.UndoManager.UndoableCommand;
import com.lomekwi.cave.util.Duplicatable;

import static com.lomekwi.cave.util.Ranges.shift;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

@NullMarked
public class Timeline implements Serializable,Iterable<Track>, Duplicatable<Timeline> {
    public final Project project;
    private transient boolean recording;
    private transient List<UndoableCommand> recorded = new ArrayList<>();
    private final List<Track> tracks = new ArrayList<>();
    private long length;
    private boolean lengthChanged = true;
    @Serial
    private static final long serialVersionUID = 1L;

    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        for (Track track : tracks) {
            track.setTimeline(this);
        }
        recording = false;
        recorded = new ArrayList<>();
    }

    public Timeline(Project project) {
        this.project = project;
    }
    public long tryAdd(Track track, Segment segment, Range<Long> range){
        long shift = track.tryAdd(segment, range);
        if (shift == 0) {
            push(new AddSegCommand(track, segment, range));
        }
        return shift;
    }
    protected void override(Track track, Segment segment, Range<Long> range){
        track.override(segment, range);
        push(new AddSegCommand(track, segment, range));
    }
    public void remove(Segment segment){
        var track = segment.getTrack();
        var range = segment.getRange();
        if (track != null && range != null && track.remove(segment)) {
            push(new RemoveSegCommand(track, segment, range, segment.getGroup()));
        }
    }
    public void remove(Collection<Segment> segments){
        List<RemoveSegsCommand.RemoveEntry> entries = new ArrayList<>(segments.size());
        for(var s : segments){
            var track = s.getTrack();
            var range = s.getRange();
            if (track != null && range != null && track.remove(s)) {
                entries.add(new RemoveSegsCommand.RemoveEntry(track, s, range, s.getGroup()));
            }
        }
        if (!entries.isEmpty()) {
            push(new RemoveSegsCommand(entries));
        }
    }

    public void split(Track track, long time) {
        var s = track.get(time);
        if (s == null) return;
        var r = s.getRange();
        long lo = r.lowerEndpoint();
        long hi = r.upperEndpoint();
        if (time <= lo || time >= hi) return;
        if (track.split(time)) {
            var right = track.get(time);
            push(new SplitSegCommand(track, s, r, right, time));
        }
    }
    public void split(long time){
        for(var t : tracks){
            split(t, time);
        }
    }

    /** 裁切一组片段的起始边缘（各自终点不变）。@return 实际应用的偏移量（截断到最大可用量），0 表示未移动。 */
    public long setStart(Collection<Segment> segments,long deltaTime){
        return applyPerTrack(segments, deltaTime, false);
    }

    /** 裁切一组片段的结束边缘（各自起点不变）。@return 同 {@link #setStart}。 */
    public long setEnd(Collection<Segment> segments,long deltaTime){
        return applyPerTrack(segments, deltaTime, true);
    }

    /** 各轨道 probe 后取限制最严者，把 deltaTime 同向截断并应用。@return 实际应用的偏移量，0 表示未移动。 */
    private long applyPerTrack(Collection<Segment> segments, long deltaTime, boolean end){
        if (deltaTime == 0 || segments.isEmpty()) return 0;
        boolean forward = deltaTime > 0;
        Set<Track> tracks = new HashSet<>();
        for (var s : segments) if (s.getTrack() != null) tracks.add(s.getTrack());
        if (tracks.isEmpty()) return 0;

        long bound = tracks.stream()
            .mapToLong(track -> end ? track.probeSetEnd(segments, forward)
                                    : track.probeSetStart(segments, forward))
            .reduce(forward ? Long.MAX_VALUE : Long.MIN_VALUE, Track::tighter);
        // 夹紧到与请求同向且不超过请求量
        long applied = forward ? Math.min(deltaTime, Math.max(bound, 0))
                               : Math.max(deltaTime, Math.min(bound, 0));
        if (applied == 0) return 0;

        // 捕获旧区间 → 修改 → 记录
        Map<Segment, Range<Long>> before = new HashMap<>();
        for (var s : segments) before.put(s, s.getRange());
        for (var track : tracks) {
            if (end) track.setEnd(segments, applied);
            else track.setStart(segments, applied);
        }
        List<ResizeSegsCommand.ResizeEntry> entries = new ArrayList<>();
        for (var s : segments) {
            var track = s.getTrack();
            var old = before.get(s);
            var r = s.getRange();
            if (track != null && old != null && r != null && !old.equals(r)) {
                entries.add(new ResizeSegsCommand.ResizeEntry(track, s, old, r));
            }
        }
        if (!entries.isEmpty()) {
            push(new ResizeSegsCommand(entries));
        }
        return applied;
    }
    /** 仅按时间平移片段（轨道不变）。deltaTime 截断到最大可用量后应用：整组最多移到与障碍贴合。@return 实际应用的偏移量；0 表示未移动。 */
    public long moveTime(Collection<Segment> segments,long deltaTime){
        if (deltaTime == 0 || segments.isEmpty()) return 0;
        boolean forward = deltaTime > 0;
        Set<Track> tracks = new HashSet<>();
        for (var s : segments) if (s.getTrack() != null) tracks.add(s.getTrack());
        if (tracks.isEmpty()) return 0;

        long bound = tracks.stream()
            .mapToLong(tr -> tr.probeMove(segments, forward))
            .reduce(forward ? Long.MAX_VALUE : Long.MIN_VALUE, Track::tighter);
        // 防御性夹紧：同向且不超过请求量
        long applied = forward ? Math.min(deltaTime, Math.max(bound, 0))
                               : Math.max(deltaTime, Math.min(bound, 0));
        if (applied == 0) return 0;

        // 先构造命令再移动（移除直接走 Track，避免重复记录）
        List<MoveSegsCommand.MoveEntry> entries = new ArrayList<>(segments.size());
        for(var s : segments){
            var r = s.getRange();
            entries.add(new MoveSegsCommand.MoveEntry(s.getTrack(), s.getTrack(), s, r, shift(r, applied)));
        }
        for(var s : segments){
            var t = s.getTrack();
            if(t != null) t.remove(s);
        }
        for(var s : segments){
            var tr = s.getTrack();
            tr.override(s,shift(s.getRange(),applied));
            s.offsetOrigin(applied);
        }
        push(new MoveSegsCommand(entries));
        return applied;
    }

    /**
     * 仅按轨道索引平移片段（时间区间不变）。deltaTrack 会被同向截断到最大可用的
     * 轨道偏移后应用：从请求的目标轨道起沿该方向逐条回退，落在第一条整组可放置的
     * 轨道上（不反向、不超过请求量），保持组内成员相对间距。
     * @return 实际应用的轨道偏移；0 表示该方向无法移动，保持原位。
     */
    public int moveTrack(Collection<Segment> segments,int deltaTrack){
        int applied = findPlaceableTrack(segments, deltaTrack);
        // applied 即本次实际落位的轨道偏移（0 表示不动）
        if (applied == 0) return 0;

        List<MoveSegsCommand.MoveEntry> entries = new ArrayList<>(segments.size());
        for(var s : segments){
            var from = s.getTrack();
            var to = getTrack(from.index + applied);
            if (from != to) {
                entries.add(new MoveSegsCommand.MoveEntry(from, to, s, s.getRange(), s.getRange()));
            }
        }
        for(var s : segments){
            var t = s.getTrack();
            if(t != null) t.remove(s);
        }
        for(var s : segments){
            getTrack(s.getTrack().index + applied).override(s, s.getRange());
        }
        if (!entries.isEmpty()) {
            push(new MoveSegsCommand(entries));
        }
        return applied;
    }

    /**
     * 在 deltaTrack 方向上找出整组可放置的最大轨道偏移（带符号，绝对值 ≤ |deltaTrack|）：
     * 从请求量开始向 0 逐级回退探测，返回第一条可放置轨道对应的偏移。
     * 索引越大的轨道越可能为空，且 getTrack 会按需创建，因此正向探测总能找到落点；
     * 反向受 0 限制，找不到时返回 0（保持原位）。
     */
    private int findPlaceableTrack(Collection<Segment> segments, int deltaTrack){
        if (deltaTrack == 0 || segments.isEmpty()) return 0;
        int minIdx = segments.stream().mapToInt(s -> s.getTrack().index).min().orElseThrow();
        int step = deltaTrack > 0 ? 1 : -1;
        int span = Math.abs(deltaTrack);
        for (int k = span; k > 0; k--) {
            // 目标轨道尚不存在（索引 ≥ tracks.size()）时视为空闲
            int target = minIdx + deltaTrack - step * (span - k);
            if (canPlaceGroupOnTrack(segments, target)) return step * k;
        }
        return 0;
    }

    /**
     * 整组按统一偏移移动后，是否每个成员在各自目标轨道上都不与既有片段冲突。
     * 目标轨道尚不存在（索引 ≥ tracks.size()）时视为空闲。
     */
    private boolean canPlaceGroupOnTrack(Collection<Segment> segments, int target){
        if (target < 0) return false;
        int refIdx = segments.iterator().next().getTrack().index;
        for (var s : segments) refIdx = Math.min(refIdx, s.getTrack().index);
        for (var s : segments){
            int ti = s.getTrack().index + (target - refIdx);
            if (ti < tracks.size() && !tracks.get(ti).isFree(s.getRange(), segments)) return false;
        }
        return true;
    }
    /** 在 [time±threshold] 内扫描所有轨道片段，返回最近的起点/终点（无则原值）；ignore 不参与。 */
    public long snapTime(long time, long threshold, Collection<Segment> ignore) {
        long best = time;
        long bestDist = threshold;
        long searchStart = Math.max(0, time - threshold);
        long searchEnd = time + threshold;
        if (searchEnd <= searchStart) return time;
        Range<Long> searchRange = Range.closedOpen(searchStart, searchEnd);
        for (Track track : tracks) {
            for (var entry : track.getSubRangeMapAsEntrySet(searchRange)) {
                if (ignore.contains(entry.getValue())) continue;
                var r = entry.getKey();
                long dist = Math.abs(r.lowerEndpoint() - time);
                if (dist < bestDist) {
                    best = r.lowerEndpoint();
                    bestDist = dist;
                }
                dist = Math.abs(r.upperEndpoint() - time);
                if (dist < bestDist) {
                    best = r.upperEndpoint();
                    bestDist = dist;
                }
            }
        }
        // 距 0 比当前最佳吸附点更近时吸附到 0
        if (time < threshold && time < bestDist) {
            best = 0;
        }
        return best;
    }

    /**
     * 开始记录：此后到 {@link #submit()} 之间对时间轴的每次修改都会记录一条命令，
     * 最终在 close/submit 时合并为一条命令提交。
     */
    public Recording record(){
        assert !recording;
        recording = true;
        recorded.clear();
        return this::submit;
    }
    /** 记录句柄，用于 try-with-resources：close() 即 {@link #submit()}。 */
    public interface Recording extends AutoCloseable {
        @Override void close();
    }
    /**
     * 结束记录，并把期间记录的所有命令合并为一条命令提交到项目的命令栈。
     * 若期间没有修改则不提交。
     */
    public void submit(){
        if (!recording) return;
        recording = false;
        if (recorded.isEmpty()) return;
        if (recorded.size() == 1) {
            project.undoManager.record(recorded.get(0));
        } else {
            project.undoManager.record(new CompoundCommand(recorded.toArray(new UndoableCommand[0])));
        }
        recorded.clear();
    }
    /** 记录模式下把一次修改对应的命令压入记录栈。同类型命令会与栈尾合并。 */
    private void push(UndoableCommand command){
        if (!recording) return;
        // 与 recording 栈中最近命令合并
        if (!recorded.isEmpty() && command instanceof MergeableCommand) {
            var last = recorded.get(recorded.size() - 1);
            if (last.getClass() == command.getClass()) {
                MergeableCommand lm = (MergeableCommand) last;
                if (lm.merge(command)) return;
            }
        }
        recorded.add(command);
    }

    /**
     * 获取指定索引的轨道，如果不存在则自动创建
     * @param index 轨道索引
     * @return 对应的轨道对象
     */
    public Track getTrack(int index) {
        while (tracks.size() <= index) {
            tracks.add(new Track(this, tracks.size()));
        }
        return tracks.get(index);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Timeline:");
        for (int i = 0; i < tracks.size(); i++) {
            sb.append(System.lineSeparator());
            sb.append("track#").append(i).append(":").append(tracks.get(i));
        }
        return sb.toString();
    }
    public long getLength(){
        if(lengthChanged){
            length=tracks.stream()
                .mapToLong(Track::getLength)
                .max()
                .orElse(0);
            lengthChanged = false;
        }
        return length;
    }
    public List<Track> getTracks() {
        return tracks;
    }

    /**
     * 两个时间线相等，当且仅当每个轨道对应相等：按索引逐位比较轨道内容。
     * 由于 {@link #getTrack(int)} 会按需自动创建空轨道、而撤销不会删除轨道，
     * 比较时把"缺失"与"空轨道"视为相等（只允许尾部为空的差异）。
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Timeline other)) return false;
        int max = Math.max(tracks.size(), other.tracks.size());
        for (int i = 0; i < max; i++) {
            Track a = i < tracks.size() ? tracks.get(i) : null;
            Track b = i < other.tracks.size() ? other.tracks.get(i) : null;
            if (!trackEquals(a, b)) return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int h = 1;
        for (Track track : tracks) {
            if (track.isEmpty()) continue;
            h = 31 * h + track.hashCode();
        }
        return h;
    }

    private static boolean trackEquals(@Nullable Track a, @Nullable Track b) {
        if (a == null) return b == null || b.isEmpty();
        if (b == null) return a.isEmpty();
        return a.equals(b);
    }

    /**
     * 迭代有元素的轨道
     * @return 轨道迭代器
     */
    @Override
    public Iterator<Track> iterator() {
        return new IteratorImpl();
    }

    public class IteratorImpl implements Iterator<Track> {
        private int index = 0;

        private void skipEmpty() {
            while (index < tracks.size() && tracks.get(index).isEmpty()) {
                index++;
            }
        }

        @Override
        public boolean hasNext() {
            skipEmpty();
            return index < tracks.size();
        }

        @Override
        public Track next() {
            if (!hasNext()) throw new java.util.NoSuchElementException();
            return tracks.get(index++);
        }
    }
}
