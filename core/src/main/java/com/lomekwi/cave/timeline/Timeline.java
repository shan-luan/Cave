package com.lomekwi.cave.timeline;

import com.lomekwi.cave.pipeline.Product;
import com.lomekwi.cave.timeline.segments.Segment;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

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
    public Timeline add(int index, Segment<?> segment, long start, long duration) {
        tracks.get(index).add(segment, start, duration);
        lengthChanged = true;
        return this;
    }
    public Timeline remove(int index,long time) {
        tracks.get(index).remove(time);
        lengthChanged = true;
        return this;
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
