package com.lomekwi.cine.output;

import com.lomekwi.cine.pipeline.Processor;
import com.lomekwi.cine.pipeline.Product;

public interface Outputable extends Product{
    Product getSource();
    default Processor getNextProcessor(){
        return null;
    }
}
