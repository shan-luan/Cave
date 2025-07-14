package com.lomekwi.cine.timeline;

import com.lomekwi.cine.pipeline.Product;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

public class Timeline {
    private final List<Track> tracks = new ArrayList<>();
    public void add(Track track) {
        tracks.add(track);
    }
    public void remove(Track track) {
        tracks.remove(track);
    }
    public void get(long time, Queue<Product> collector) {
        tracks.forEach(track -> track.get(time, collector));
    }
    public Track getTrack(int index){
        return tracks.get(index);
    }
}
