package com.lomekwi.cine.pipeline.decode;

import com.lomekwi.cine.pipeline.Processor;
import com.lomekwi.cine.pipeline.Product;

import java.util.Queue;

public interface DecProc extends Processor,AutoCloseable  {
    @Override
    void process(Product product, Queue<Product> collector);
}
