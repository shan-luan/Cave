package com.lomekwi.cine.content;

import com.lomekwi.cine.pipeline.Processor;
import com.lomekwi.cine.pipeline.Product;
import com.lomekwi.cine.timeline.Segment;


//新的Element，表示任何时间线能承载的元素
public abstract class Element implements Product {
    @Override
    public abstract Processor getNextProcessor();
}
