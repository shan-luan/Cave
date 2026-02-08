package com.lomekwi.cine.timeline;

import com.lomekwi.cine.content.Element;
import com.lomekwi.cine.pipeline.Processor;
import com.lomekwi.cine.pipeline.Product;
/**
 * 时间片段
 */
public class Seg implements Product {
    private final Element element;
    private final long start;
    protected Track track;

    //TODO:让Track封装Seg的创建逻辑
    public Seg(Element element, long start) {
        this.element = element;
        this.start = start;
    }

    public Element getElement() {
        return element;
    }

    public long getStart() {
        return start;
    }

    public long getDuration() {
        return getEnd() - start;
    }

    /**
     * 如果抛出了IndexOutOfBoundsException，请检查track的最后一个元素，可能是Gap
     */
    public long getEnd() {
        return (track.getByIndex(track.getIndexOf(this)+1)).getStart();
    }
    @Override
    public Processor getNextProcessor() {
        return element.getNextProcessor();
    }
    public boolean isGap(){return false;}

    @Override
    public String toString() {
        return "[s:"+start+" c:"+getElement()+" d:"+getDuration()+"]";
    }

    protected void setTrack(Track track) {
        this.track = track;
    }
}
