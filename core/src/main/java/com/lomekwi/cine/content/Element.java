package com.lomekwi.cine.content;

import com.lomekwi.cine.pipeline.Processor;
import com.lomekwi.cine.pipeline.Product;
import com.lomekwi.cine.timeline.Track;


//新的Element，表示任何时间线能承载的元素
public abstract class Element implements Product {
    private Track track;
    @Override
    public abstract Processor getNextProcessor();

    public Track getTrack() {
        return track;
    }

    public void setTrack(Track track) {
        if(this.track != null) return;
        this.track = track;
    }

}
