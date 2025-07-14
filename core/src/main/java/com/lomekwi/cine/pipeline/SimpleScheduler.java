package com.lomekwi.cine.pipeline;

import com.badlogic.gdx.Gdx;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class SimpleScheduler implements Scheduler {
    private final List<Product> returnProducts = new LinkedList<>();
    @Override
    public void start(Queue<Product> initialProducts) {
        returnProducts.clear();
        while (!initialProducts.isEmpty()) {
            Product product = initialProducts.poll();
            Processor processor = product.getNextProcessor();

            if (processor != null) {
                processor.process(product, initialProducts);
            }else {
                returnProducts.add(product);
            }
        }
        initialProducts.addAll(returnProducts);
    }

    @Override
    public void setThreads(int threads){
        Gdx.app.error("Cine", "setThreads() is not supported");
    }
}
