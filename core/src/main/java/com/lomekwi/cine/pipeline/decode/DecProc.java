package com.lomekwi.cine.pipeline.decode;

import com.lomekwi.cine.pipeline.Processor;
import com.lomekwi.cine.pipeline.Product;

import org.bytedeco.javacv.FrameGrabber;

import java.util.Queue;

public interface DecProc extends Processor  {
    @Override
    void process(Product product, Queue<Product> collector) throws FrameGrabber.Exception;
}
