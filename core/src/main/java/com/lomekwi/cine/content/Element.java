package com.lomekwi.cine.content;

import com.lomekwi.cine.pipeline.Product;

public interface Element extends Product {
    public long getStart();
    public long getEnd();
    public void dispose();
}
