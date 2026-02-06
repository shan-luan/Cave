package com.lomekwi.cine.timeline;

import com.lomekwi.cine.content.Element;
import com.lomekwi.cine.pipeline.Product;
import com.lomekwi.cine.util.intervaltree.Interval;
import com.lomekwi.cine.util.intervaltree.IntervalTree;

import java.util.Queue;
//TODO:把区间树改成arraylist，区间树实现不稳定
public class Track {
    private final IntervalTree<Segment<?>> segments = new IntervalTree<>();
    private final Timeline timeline;

    public Track(Timeline timeline) {
        this.timeline = timeline;
    }

    public void add(Segment<?> segment) {
        segments.addInterval(new Interval<>(segment.getStart(), segment.getEnd(), segment));
        segments.build();

        timeline.onElementAdded(this, segment.getElement());
    }
    public void get(long time, Queue<Product> collector) {
        collector.addAll(segments.get(time));
    }
    public void remove(Segment<?> segment) {
        segments.removeInterval(new Interval<>(segment.getStart(), segment.getEnd(), segment));
        segments.build();

        timeline.onElementRemoved( this, segment.getElement());
    }
}
