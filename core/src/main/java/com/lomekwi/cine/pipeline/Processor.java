package com.lomekwi.cine.pipeline;

import java.util.Queue;

public interface Processor<T extends Product> {
    void process(T product, Queue<Product> collector);
}
