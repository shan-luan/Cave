package com.lomekwi.cine.timeline;

import com.lomekwi.cine.pipeline.Product;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

public class Timeline {
    private final List<Track> tracks = new ArrayList<>();
    public Timeline add() {
        Track track = new Track(this);
        tracks.add(track);
        return this;
    }
    public Timeline remove(Track track) {
        tracks.remove(track);
        return this;
    }
    public Timeline get(long time, Queue<Product> collector) {
        for(Track track:tracks){
            Product product = track.get(time);
            collector.add(product);
        }
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
}
