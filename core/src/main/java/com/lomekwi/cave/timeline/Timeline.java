package com.lomekwi.cave.timeline;

import com.google.common.collect.Range;
import com.lomekwi.cave.pipeline.Frame;
import com.lomekwi.cave.project.Project;
import com.lomekwi.cave.util.Duplicatable;

import org.jspecify.annotations.NullMarked;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import static java.util.Map.Entry;

@NullMarked
public class Timeline implements Serializable,Iterable<Track>, Duplicatable<Timeline> {
    public final Project project;
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
    }

    public Timeline(Project project) {
        this.project = project;
    }
    public Timeline add(Track track, Segment segment, long start, long duration) {
        track.add(segment, start, duration);
        lengthChanged = true;
        return this;
    }
    public Timeline remove(Track track,long time) {
        track.remove(time);
        lengthChanged = true;
        return this;
    }
    public Timeline remove(Track track,long start,long duration) {
        track.remove(start,duration);
        lengthChanged = true;
        return this;
    }
    public Timeline remove(Track track,Range<Long> range) {
        track.remove(range);
        lengthChanged = true;
        return this;
    }
    public Timeline resize(Track track, Entry<Range<Long>, Segment> e, long start, long duration) {
        track.resize(e,start,duration);
        lengthChanged = true;
        return this;
    }
    public Timeline move(Track track, Track newTrack, Entry<Range<Long>, Segment> e, long start, long duration) {
        track.remove(e.getKey());
        newTrack.add(e.getValue(),start,duration);
        lengthChanged = true;
        return this;
    }
    public Timeline split(Track track,long time) {
        track.split(time);
        return this;
    }

    /**
     * 检查组移动是否有效——所有成员的目标位置均空闲且组内互不阻塞
     */
    public boolean canMoveGroup(List<Segment> members, long[] newStarts, long[] newDurations, Track[] newTracks) {
        int n = members.size();
        Set<Segment> ignore = new HashSet<>(members);
        for (int i = 0; i < n; i++) {
            if (newStarts[i] < 0 || newDurations[i] <= 0) return false;
            var range = Range.closedOpen(newStarts[i], newStarts[i] + newDurations[i]);
            if (!newTracks[i].isFree(range, ignore)) return false;
        }
        return true;
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
