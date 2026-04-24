package com.lomekwi.cave.pipeline;

import java.io.Serializable;

public interface Filter<T> extends Serializable {
    void filter(T product);
}
