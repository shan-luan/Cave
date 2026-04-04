package com.lomekwi.cave.timeline;

import com.google.common.collect.Range;
import com.lomekwi.cave.pipeline.Product;

import org.jspecify.annotations.NullMarked;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@NullMarked
public class Timeline implements Serializable {
    private final List<Track> tracks = new ArrayList<>();
    private long length;
    private boolean lengthChanged = true;
    private static final long serialVersionUID = 1L;
    public Timeline addTrack() {
        tracks.add(new Track());
        return this;
    }
    public Timeline removeTrack(Track track) {
        tracks.remove(track);
        return this;
    }
    public Timeline getActiveElements(long time, Collection<Product> collector) {
        for(Track track:tracks){
            Product product = track.get(time);
            if(product==null) continue;
            collector.add(product);
        }
        return this;
    }
    public Timeline add(Track track, SegmentData<?> segmentData, long start, long duration) {
        track.add(segmentData, start, duration);
        segmentData.setTrack(track);
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
    public Timeline resize(Track track, Map.Entry<Range<Long>, SegmentData<?>> e, long start, long duration) {
        track.resize(e,start,duration);
        lengthChanged = true;
        return this;
    }
    public Track getTrack(int index) {
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
