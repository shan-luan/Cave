package com.lomekwi.cave.app;


import com.google.common.eventbus.EventBus;
import com.lomekwi.cave.task.TaskPool;
import com.lomekwi.cave.ui.Root;

import games.spooky.gdx.nativefilechooser.NativeFileChooser;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class App {
    public static NativeFileChooser fileChooser;
    public static AppAudioOut audioOut;
    public static final EventBus appEventBus = new EventBus();
    public static final ExecutorService workerExecutor = Executors.newCachedThreadPool(r -> {
        Thread thread = new Thread(r);
        thread.setDaemon(true);
        return thread;
    });
    public static final ExecutorService audioExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r);
        thread.setDaemon(true);
        thread.setPriority(Thread.MAX_PRIORITY);
        return thread;
    });
    public static final TaskPool taskPool = new TaskPool();
    public static Root root;
}
