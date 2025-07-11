package com.lomekwi.cine.pipeline;

import java.util.Queue;

public interface Scheduler {
    void start(Queue<Product> initialProducts);
    void setThreads(int threads);
}
