package com.lomekwi.cine.pipeline;

import com.badlogic.gdx.Gdx;

import java.util.Queue;

public class SimpleScheduler implements Scheduler {
    @Override
    public void start(Queue<Product> initialProducts) {
        while (!initialProducts.isEmpty()) {
            Product product = initialProducts.poll();
            Processor processor = product.getNextProcessor();

            if (processor != null) {
                processor.process(product, initialProducts);
            }
        }
    }

    @Override
    public void setThreads(int threads){
        Gdx.app.error("Cine", "setThreads() is not supported");
    }
}
