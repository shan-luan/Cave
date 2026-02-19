package com.lomekwi.cine.pipeline;

public interface Sink<T extends Product> {
    public void sink(T product);
}
