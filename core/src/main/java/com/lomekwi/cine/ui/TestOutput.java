package com.lomekwi.cine.ui;

import com.lomekwi.cine.output.Outputable;
import com.lomekwi.cine.output.Outputter;
import com.lomekwi.cine.pipeline.render.PixmapProduct;

public class TestOutput implements Outputter {
    private Outputable outputable;
    public TestOutput(Root root) {
        root.getProject().getPlayController().addOutput(PixmapProduct.class, this);
    }
    @Override
    public void output(Outputable outputable) {
        this.outputable = outputable;
        System.out.println("new product!"+ this.outputable);
    }

    public void update(){
        System.out.println("TestOutput:"+ outputable);
    }
}
