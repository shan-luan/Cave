package com.lomekwi.cine.pipeline;

import java.util.Queue;

public interface Processor {
    void process(Product product, Queue<Product> collector);
}
