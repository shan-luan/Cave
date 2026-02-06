package com.lomekwi.cine.timeline;

import com.lomekwi.cine.content.Element;
import com.lomekwi.cine.pipeline.Processor;
import com.lomekwi.cine.pipeline.Product;

//TODO:用此类拆分现有的Element的时间区间部分逻辑，目前的Element可以删了
public class Segment<T extends Element> implements Product {
    private final T element;
    private final long start;
    private final long duration;

    public Segment(T element, long start, long duration) {
        this.element = element;
        this.start = start;
        this.duration = duration;
    }

    public T getElement() {
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
    public Processor<?> getNextProcessor() {
        return element.getNextProcessor();
    }
}
