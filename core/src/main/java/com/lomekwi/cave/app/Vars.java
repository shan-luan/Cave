package com.lomekwi.cave.app;


import com.google.common.eventbus.EventBus;

import games.spooky.gdx.nativefilechooser.NativeFileChooser;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class Vars {
    public static NativeFileChooser fileChooser;
    public static final EventBus appEventBus = new EventBus();
    public static final ExecutorService trackExecutor = Executors.newCachedThreadPool(r -> {
        Thread thread = new Thread(r);
        thread.setDaemon(true);
        return thread;
    });
}
