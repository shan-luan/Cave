package com.lomekwi.cave.timeline;

import com.google.common.collect.Range;
import com.lomekwi.cave.project.Project;
import com.lomekwi.cave.timeline.UndoManager.AddSegCommand;
import com.lomekwi.cave.timeline.UndoManager.CompoundCommand;
import com.lomekwi.cave.timeline.UndoManager.MoveSegCommand;
import com.lomekwi.cave.timeline.UndoManager.RemoveSegCommand;
import com.lomekwi.cave.timeline.UndoManager.ResizeSegCommand;
import com.lomekwi.cave.timeline.UndoManager.SplitSegCommand;
import com.lomekwi.cave.timeline.UndoManager.UndoableCommand;
import com.lomekwi.cave.util.Duplicatable;

import static com.lomekwi.cave.util.Ranges.shift;

import org.jspecify.annotations.NullMarked;

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
            push(new AddSegCommand(track, segment, range.lowerEndpoint(), range.upperEndpoint() - range.lowerEndpoint()));
        }
        return shift;
    }
    protected void override(Track track, Segment segment, Range<Long> range){
        track.override(segment, range);
        push(new AddSegCommand(track, segment, range.lowerEndpoint(), range.upperEndpoint() - range.lowerEndpoint()));
    }
    public void remove(Segment segment){
        var track = segment.getTrack();
        var range = segment.getRange();
        if (track != null && range != null && track.remove(segment)) {
            push(new RemoveSegCommand(track, segment, range.lowerEndpoint(), range.upperEndpoint() - range.lowerEndpoint(), segment.getGroup()));
        }
    }
    public void remove(Collection<Segment> segments){
        for(var s : segments){
            remove(s);
        }
    }

    public void split(Track track, long time) {
        var e = track.getEntry(time);
        if (e == null) return;
        var s = e.getValue();
        var r = s.getRange();
        long lo = r.lowerEndpoint();
        long hi = r.upperEndpoint();
        if (time <= lo || time >= hi) return;
        if (track.split(time)) {
            var right = track.getEntry(time).getValue();
            push(new SplitSegCommand(track, s, lo, hi - lo, right, time));
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
        Set<Track> tracks = new HashSet<>();
        for (var s : segments) if (s.getTrack() != null) tracks.add(s.getTrack());

        long max = 0;
        for (var track : tracks) {
            long d = end ? track.probeSetEnd(segments, deltaTime)
                         : track.probeSetStart(segments, deltaTime);
            max = Math.abs(d) > Math.abs(max) ? d : max;
        }
        if (max == 0) {
            // 先捕获各片段的旧区间，再执行修改，最后逐片段记录命令
            Map<Segment, Range<Long>> before = new HashMap<>();
            for (var s : segments) before.put(s, s.getRange());
            for (var track : tracks) {
                if (end) track.setEnd(segments, deltaTime);
                else track.setStart(segments, deltaTime);
            }
            for (var s : segments) {
                var track = s.getTrack();
                var old = before.get(s);
                var r = s.getRange();
                if (track != null && old != null && r != null
                    && (old.lowerEndpoint() != r.lowerEndpoint() || old.upperEndpoint() != r.upperEndpoint())) {
                    push(new ResizeSegCommand(track, s,
                        old.lowerEndpoint(), old.upperEndpoint() - old.lowerEndpoint(),
                        r.lowerEndpoint(), r.upperEndpoint() - r.lowerEndpoint()));
                }
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
            List<MoveSegCommand> cmds = new ArrayList<>(segments.size());
            for(var s : segments){
                var from = s.getTrack();
                var r = s.getRange();
                long oldStart = r.lowerEndpoint();
                long oldDur = r.upperEndpoint() - r.lowerEndpoint();
                var to = getTrack(from.index + deltaTrack);
                if (deltaTime != 0 || from != to) {
                    cmds.add(new MoveSegCommand(from, to, s, oldStart, oldDur, oldStart + deltaTime, oldDur));
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
            for(var cmd : cmds){
                push(cmd);
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
        project.undoManager.record(new CompoundCommand(recorded.toArray(new UndoableCommand[0])));
        recorded.clear();
    }
    /** 记录模式下把一次修改对应的命令压入记录栈。 */
    private void push(UndoableCommand command){
        if (recording) recorded.add(command);
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
