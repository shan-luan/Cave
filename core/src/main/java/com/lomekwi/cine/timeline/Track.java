package com.lomekwi.cine.timeline;

import com.lomekwi.cine.pipeline.Product;


import java.util.ArrayList;
import java.util.Queue;
public class Track {
    private final ArrayList<Seg> segments = new ArrayList<>();
    private final Timeline timeline;


    public Track(Timeline timeline) {
        this.timeline = timeline;
    }

    public void add(Seg seg) {
        //TODO:重叠检查未完成
        //TODO:需要确保自动补全Gap
        int i = binarySearch(segments, seg.getStart());
        if(i < 0){
            i = -(i + 1);
        }
        segments.add(i, seg);
        seg.setTrack(this);

        timeline.onElementAdded(this, seg.getElement());
    }
    public void get(long time, Queue<Product> collector) {
        //FIXME:time超出边界时，不应返回最后一个元素
        int i = binarySearch(segments, time);
        if(i < 0){
            i = (-(i+1))-1;
        }
        collector.add(segments.get(i));

        System.out.println("get:"+segments.get(i));
    }

    public Seg getByIndex(int index) {
        return segments.get(index);
    }
    public Seg getLastSeg() {
        return segments.get(segments.size()-1);
    }
    public int getIndexOf(Seg seg){
        return binarySearch(segments, seg.getStart());//因为传入开始时间所以必定找到
    }
    public void remove(long time) {
        //TODO:对Gap处理
        Seg seg = segments.get(binarySearch(segments, time));
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
    private static int binarySearch(ArrayList<Seg> list, long key) {
        int low = 0;
        int high = list.size() - 1;

        while (low <= high) {
            int mid = (low + high) >>> 1;
            long midVal = list.get(mid).getStart();

            if (midVal < key) {
                low = mid + 1;
            } else if (midVal > key) {
                high = mid - 1;
            } else {
                return mid;
            }
        }
        return -(low + 1);
    }
}
