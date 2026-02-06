package com.lomekwi.cine.pipeline.decode;

import com.lomekwi.cine.content.Clip;
import com.lomekwi.cine.pipeline.Processor;
import com.lomekwi.cine.pipeline.Product;
import com.lomekwi.cine.resource.Media;
import com.lomekwi.cine.timeline.Segment;

import java.util.Queue;

public interface Decoder<T extends Media<?>> extends Processor<Segment<Clip<T>>>,AutoCloseable  {
    @Override
    void process(Segment<Clip<T>> product, Queue<Product> collector);
}
