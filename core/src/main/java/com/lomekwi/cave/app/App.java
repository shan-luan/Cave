package com.lomekwi.cave.app;


import com.google.common.eventbus.EventBus;
import com.lomekwi.cave.app.copy.CopyManager;
import com.lomekwi.cave.app.shortcut.ShortcutManager;
import com.lomekwi.cave.pipeline.FilterRegistry;
import com.lomekwi.cave.resource.media.MediaFactory;
import com.lomekwi.cave.task.TaskPool;
import com.lomekwi.cave.ui.Root;

import games.spooky.gdx.nativefilechooser.NativeFileChooser;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class App {
    private App() {}
    public static NativeFileChooser fileChooser;
    public static AppAudioOut audioOut;
    public static final EventBus appEventBus = new EventBus();
    public static final ExecutorService workerExecutor = Executors.newCachedThreadPool(r -> {
        Thread thread = new Thread(r);
        thread.setDaemon(true);
        return thread;
    });
    public static final TaskPool taskPool = new TaskPool();
    public static Root root;
    public static final ShortcutManager shortcutManager = new ShortcutManager();
    public static final CopyManager copyManager = new CopyManager();
    public static final MediaFactory mediaFactory = new MediaFactory();
    public static final FilterRegistry filterRegistry = new FilterRegistry();

}
