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

    public long setStart(Collection<Segment> segments,long deltaTime){
        return applyPerTrack(segments, deltaTime, false);
    }

    public long setEnd(Collection<Segment> segments,long deltaTime){
        return applyPerTrack(segments, deltaTime, true);
    }

    private long applyPerTrack(Collection<Segment> segments, long deltaTime, boolean end){
        if (deltaTime == 0) return 0;
        Set<Track> tracks = new HashSet<>();
        for (var s : segments) if (s.getTrack() != null) tracks.add(s.getTrack());

        long max = 0;
        for (var track : tracks) {
            long d = end ? track.probeSetEnd(segments, deltaTime)
                         : track.probeSetStart(segments, deltaTime);
            max = Math.abs(d) > Math.abs(max) ? d : max;
        }
        if (max == 0) {
            // 先捕获各片段的旧区间，再执行修改，最后批量记录命令
            Map<Segment, Range<Long>> before = new HashMap<>();
            for (var s : segments) before.put(s, s.getRange());
            for (var track : tracks) {
                if (end) track.setEnd(segments, deltaTime);
                else track.setStart(segments, deltaTime);
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
        }
        return max;
    }
    public long move(Collection<Segment> segments,long deltaTime,int deltaTrack){
        long max=0;
        for(var s : segments){
            var tr = getTrack(s.getTrack().index+deltaTrack);
            var t = shift(s.getRange(),deltaTime);
            var d = tr.getShift(t,segments);
            max=Math.abs(d)>Math.abs(max)?d : max;
        }
        if(max==0){
            // 先按旧状态构造命令，再执行移动（内部移除直接走 Track，避免重复记录）
            List<MoveSegsCommand.MoveEntry> entries = new ArrayList<>(segments.size());
            for(var s : segments){
                var from = s.getTrack();
                var r = s.getRange();
                var to = getTrack(from.index + deltaTrack);
                if (deltaTime != 0 || from != to) {
                    entries.add(new MoveSegsCommand.MoveEntry(from, to, s, r, shift(r, deltaTime)));
                }
            }
            for(var s : segments){
                var t = s.getTrack();
                if(t != null) t.remove(s);
            }
            for(var s : segments){
                var tr = getTrack(s.getTrack().index+deltaTrack);
                tr.override(s,shift(s.getRange(),deltaTime));
                s.offsetOrigin(deltaTime);
            }
            if (!entries.isEmpty()) {
                push(new MoveSegsCommand(entries));
            }
        }
        return max;
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
            @Nullable Track a = i < tracks.size() ? tracks.get(i) : null;
            @Nullable Track b = i < other.tracks.size() ? other.tracks.get(i) : null;
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
