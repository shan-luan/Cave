package com.lomekwi.cave.task;

import com.lomekwi.cave.app.App;

public interface Task extends Runnable,AutoCloseable{
    /**
     * [0,1]
     */
    float getProgress();
    default String getName(){
        return toString();
    }
    default TaskPool getPool(){
        return App.taskPool;
    }
}
