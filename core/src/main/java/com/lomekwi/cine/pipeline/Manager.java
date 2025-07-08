package com.lomekwi.cine.pipeline;

import java.util.Queue;

public interface Manager {
    void start(Queue<Product> initialProducts);
    void setThreads(int threads);
}
