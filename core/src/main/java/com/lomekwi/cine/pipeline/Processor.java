package com.lomekwi.cine.pipeline;

import org.bytedeco.javacv.FrameGrabber;

import java.util.Queue;

public interface Processor {
    void process(Product product, Queue<Product> collector) throws FrameGrabber.Exception;
}
