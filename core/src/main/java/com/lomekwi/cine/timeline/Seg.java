package com.lomekwi.cine.timeline;

import com.lomekwi.cine.content.Element;
import com.lomekwi.cine.pipeline.Processor;
import com.lomekwi.cine.pipeline.Product;
/**
 * 时间片段
 */
public class Seg implements Product,Comparable<Long> {
    private final Element element;
    private final long start;
    private final long duration;


    public Seg(Element element, long start, long duration) {
        this.element = element;
        this.start = start;
        this.duration = duration;
    }

    public Element getElement() {
        return element;
    }

    public long getStart() {
        return start;
    }

    public long getDuration() {
        return duration;
    }
    public long getEnd() {
        return start + duration;
    }
    @Override
    public Processor getNextProcessor() {
        return element.getNextProcessor();
    }
    @Override
    public int compareTo(Long o) {
        return Long.compare(start, o);
    }
    public boolean isGap(){return false;}

    @Override
    public String toString() {
        return "[s:"+start+" c:"+getElement()+" d:"+duration+"]";
    }
}
