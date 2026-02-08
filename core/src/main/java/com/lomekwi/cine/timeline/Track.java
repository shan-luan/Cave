package com.lomekwi.cine.timeline;

import com.lomekwi.cine.pipeline.Product;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
public class Track {
    private final List<Seg> segments = new ArrayList<>();
    private final Timeline timeline;
    private int currentSegIndex = -1;

    public Track(Timeline timeline) {
        this.timeline = timeline;
    }

    public void add(Seg seg) {
        //TODO:重叠检查未完成
        //TODO:需要确保自动补全Gap
        int i = Collections.binarySearch(segments, seg.getStart());
        if(i < 0){
            i = -(i + 1);
        }
        segments.add(i, seg);

        timeline.onElementAdded(this, seg.getElement());
    }
    public void get(long time, Queue<Product> collector) {
        //FIXME:time超出边界时，不应返回最后一个元素
        int i = Collections.binarySearch(segments, time);
        if(i < 0){
            i = -(i+2);
        }
        collector.add(segments.get(i));
        currentSegIndex = i;
    }
    protected Seg getByIndex(int deltaIndex){
        return segments.get(currentSegIndex+deltaIndex);
    }
    public void remove(long time) {
        //TODO:对Gap处理
        Seg seg = segments.get(Collections.binarySearch(segments, time));
        segments.remove(seg);

        timeline.onElementRemoved( this, seg.getElement());
    }
    //for debug
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < segments.size(); i++) {
            if(segments.get(i).isGap()){
                sb.append("Gap#").append(i).append(":").append(segments.get(i));
            }
            sb.append("Seg#").append(i).append(":").append(segments.get(i));
        }
        return sb.toString();
    }
}
