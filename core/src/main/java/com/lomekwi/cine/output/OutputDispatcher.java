package com.lomekwi.cine.output;

import com.lomekwi.cine.pipeline.Product;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class OutputDispatcher {
    private final Map<Class<? extends Product>, Set<Outputter>> outputs= new HashMap<>();
    public void addOutput(Class<? extends Product> productClass, Outputter output){
        outputs.computeIfAbsent(productClass, k -> new HashSet<>()).add(output);
    }
    public void removeOutput(Class<? extends Product> productClass, Outputter output){
        outputs.computeIfPresent(productClass, (k, set) -> {
            set.remove(output);
            return set.isEmpty() ? null : set;
        });
    }
    public void output(Product product){
        Set<Outputter> outputSet = outputs.get(product.getClass());
        if (outputSet != null) {
            outputSet.forEach(output -> output.output((Outputable) product));
        }
    }
    public void resetAll(){
        outputs.values().forEach(s -> s.forEach(Outputter::reset));
    }
}
