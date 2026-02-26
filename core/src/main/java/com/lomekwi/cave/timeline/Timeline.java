package com.lomekwi.cave.timeline;

import com.lomekwi.cave.pipeline.Product;
import com.lomekwi.cave.pipeline.Source;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class Timeline {
    private final List<Track> tracks = new ArrayList<>();
    private long length;
    private boolean lengthChanged = true;
    public Timeline addTrack() {
        Track track = new Track(this);
        tracks.add(track);
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
    public Timeline add(int index,Source<?> src, long start, long duration) {
        tracks.get(index).add(src,start,duration);
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
        }
        return length;
    }
}
