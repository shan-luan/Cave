package com.lomekwi.cine.pipeline.filter;

import com.lomekwi.cine.pipeline.product.Product;

public interface Filter <T extends Product>{
    public T filter(T product);
}
