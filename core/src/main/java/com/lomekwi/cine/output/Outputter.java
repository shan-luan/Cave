package com.lomekwi.cine.output;

import com.lomekwi.cine.pipeline.Product;

public interface Outputter {
    void output(Outputable outputable);
    void reset();
}
