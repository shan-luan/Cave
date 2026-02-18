package com.lomekwi.cine.pipeline.image;

import com.lomekwi.cine.pipeline.Product;

public class ImgProd implements Product, Transformable {
    private Transform transform;
    public ImgProd() {
    }
    @Override
    public Transform getTransform() {
        return transform;
    }
    @Override
    public void setTransform(Transform transform) {
        this.transform = transform;
    }
}
