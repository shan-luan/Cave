package com.lomekwi.cave.task;

public interface Task extends Runnable{
    /**
     * [0,1]
     */
    float getProgress();
    default String getName(){
        return toString();
    }
}
