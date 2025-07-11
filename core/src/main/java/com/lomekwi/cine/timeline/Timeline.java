package com.lomekwi.cine.timeline;

import com.lomekwi.cine.pipeline.Product;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Queue;

public class Timeline {
    private final Collection<Track> tracks = new ArrayList<>();
    public void add(Track track) {
        tracks.add(track);
    }
    public void remove(Track track) {
        tracks.remove(track);
    }
    public void get(long time, Queue<Product> collector) {
        tracks.forEach(track -> track.get(time, collector));
    }
}
