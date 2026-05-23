package com.lomekwi.cave.timeline;

import com.google.common.collect.Range;
import com.lomekwi.cave.pipeline.Frame;
import com.lomekwi.cave.project.Project;

import org.jspecify.annotations.NullMarked;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@NullMarked
public class Timeline implements Serializable {
    public final Project project;
    private final List<Track> tracks = new ArrayList<>();
    private long length;
    private boolean lengthChanged = true;
    private static final long serialVersionUID = 1L;

    public Timeline(Project project) {
        this.project = project;
    }
    public Timeline getActiveElements(long time, Collection<Frame> collector) {
        for(Track track:tracks){
            Frame frame = track.get(time);
            if(frame ==null) continue;
            collector.add(frame);
        }
        return this;
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
    public Timeline resize(Track track, Map.Entry<Range<Long>, Segment> e, long start, long duration) {
        track.resize(e,start,duration);
        lengthChanged = true;
        return this;
    }
    public Timeline move(Track track, Track newTrack, Map.Entry<Range<Long>, Segment> e, long start, long duration) {
        track.remove(e.getKey());
        newTrack.add(e.getValue(),start,duration);
        lengthChanged = true;
        return this;
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
}
