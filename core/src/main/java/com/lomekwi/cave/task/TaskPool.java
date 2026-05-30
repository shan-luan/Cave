package com.lomekwi.cave.task;

import com.lomekwi.cave.app.App;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class TaskPool implements Iterable<Task>{
    private final Set<Task> tasks = new HashSet<>();
    public void submit(Task task) {
        tasks.add(task);
        App.workerExecutor.submit(task);
    }

    @Override
    public Iterator<Task> iterator() {
        return tasks.iterator();
    }
}
