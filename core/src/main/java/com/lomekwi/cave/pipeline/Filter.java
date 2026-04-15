package com.lomekwi.cave.pipeline;

import java.io.Serializable;

public interface Filter extends Serializable {
    public void filter(Object product);
}
