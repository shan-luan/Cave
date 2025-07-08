package com.lomekwi.cine.timeline;

import com.lomekwi.cine.content.Element;
import com.lomekwi.cine.pipeline.Product;
import com.lomekwi.cine.util.intervaltree.Interval;
import com.lomekwi.cine.util.intervaltree.IntervalTree;

import java.util.Queue;

public class Track {
    private final IntervalTree<Element> elements = new IntervalTree<>();
    public void add(Element element) {
        elements.addInterval(new Interval<>(element.getStart(), element.getEnd(), element));
        elements.build();
    }
    public void get(long time, Queue<Product> collector) {
        collector.addAll(elements.get(time));
    }
    public void remove(Element element) {
        elements.removeInterval(new Interval<>(element.getStart(), element.getEnd(), element));
        elements.build();
        element.dispose();
    }
}
