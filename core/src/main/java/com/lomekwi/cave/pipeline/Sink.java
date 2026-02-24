package com.lomekwi.cave.pipeline;

public interface Sink<T extends Product> {
    public void sink(T product);
}
