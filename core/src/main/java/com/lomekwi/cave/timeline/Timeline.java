package com.lomekwi.cave.timeline;

import com.google.common.collect.Range;
import com.lomekwi.cave.project.Project;
import com.lomekwi.cave.util.Duplicatable;

import static com.lomekwi.cave.util.Ranges.shift;

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

    public void remove(Segment segment){
        segment.getTrack().remove(segment);
    }
    public void remove(Collection<Segment> segments){
        for(var s : segments){
            remove(s);
        }
    }

    public void add(Track track, Segment segment, Range<Long> range){
        track.override(segment, range);
    }

    public void split(Track track, long time) {
        track.split(time);
    }
    public void split(long time){
        for(var t : tracks){
            t.split(time);
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
            for (var track : tracks) {
                if (end) track.setEnd(segments, deltaTime);
                else track.setStart(segments, deltaTime);
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
            remove(segments);
            for(var s : segments){
                var tr = getTrack(s.getTrack().index+deltaTrack);
                tr.override(s,shift(s.getRange(),deltaTime));
                s.offsetOrigin(deltaTime);
            }
        }
        return max;
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
