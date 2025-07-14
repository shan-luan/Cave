package com.lomekwi.cine.ui;

import com.lomekwi.cine.output.Outputable;
import com.lomekwi.cine.pipeline.Product;
import com.lomekwi.cine.pipeline.render.PixmapProduct;

public class TestOutput implements Outputable {
    private Product product;
    public TestOutput(Root root) {
        root.getProject().getPlayController().addOutput(PixmapProduct.class, this);
    }
    @Override
    public void output(Product product) {
        this.product = product;
        System.out.println("new product!"+ product);
    }

    public void update(){
        System.out.println("TestOutput:"+product);
    }
}
