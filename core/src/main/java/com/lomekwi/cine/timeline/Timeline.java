package com.lomekwi.cine.timeline;

import com.lomekwi.cine.content.Element;
import com.lomekwi.cine.pipeline.Product;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

public class Timeline {
    private final List<Track> tracks = new ArrayList<>();
    private final List<TimelineObserver> observers = new ArrayList<>();
    public Timeline add() {
        Track track = new Track(this);
        tracks.add(track);
        observers.forEach(o -> o.onTrackAdded(track));
        return this;
    }
    public Timeline remove(Track track) {
        tracks.remove(track);
        observers.forEach(o -> o.onTrackRemoved(track));
        return this;
    }
    public Timeline get(long time, Queue<Product> collector) {
        tracks.forEach(track -> track.get(time, collector));
        return this;
    }
    public Track getTrack(int index) {
        return tracks.get(index);
    }
    public void addObserver(TimelineObserver observer) {
        observers.add(observer);
    }
    public void removeObserver(TimelineObserver observer) {
        observers.remove(observer);
    }
    protected void onElementAdded(Track track, Element element) {
        observers.forEach(o -> o.onElementAdded(track, element));
    }
    protected void onElementRemoved(Track track, Element element) {
        observers.forEach(o -> o.onElementRemoved(track, element));
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
