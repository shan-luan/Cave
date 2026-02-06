package com.lomekwi.cine.timeline;

import com.lomekwi.cine.pipeline.Product;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
public class Track {
    private final List<Segment> segments = new ArrayList<>();
    private final Timeline timeline;

    public Track(Timeline timeline) {
        this.timeline = timeline;
    }

    public void add(Segment segment) {
        //TODO:重叠检查未完成
        //TODO:需要确保自动补全Gap
        int i = Collections.binarySearch(segments, segment.getStart());
        if(i < 0){
            i = -(i + 1);
        }
        segments.add(i,segment);

        timeline.onElementAdded(this, segment.getElement());
    }
    public void get(long time, Queue<Product> collector) {
        //FIXME:time超出边界时，不应返回最后一个元素
        int i = Collections.binarySearch(segments, time);
        if(i < 0){
            i = -(i+2);
        }
        System.out.println("get:"+i);
        collector.add(segments.get(i));
    }
    public void remove(long time) {
        //TODO:对Gap处理
        Segment segment = segments.get(Collections.binarySearch(segments, time));
        segments.remove(segment);

        timeline.onElementRemoved( this, segment.getElement());
    }
}
