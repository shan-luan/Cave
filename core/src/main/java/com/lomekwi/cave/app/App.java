package com.lomekwi.cave.app;


import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.google.common.eventbus.EventBus;
import com.kotcrab.vis.ui.widget.VisTextField;
import com.lomekwi.cave.app.shortcut.ShortcutManager;
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

    public static boolean isTextInputFocused() {
        var stage = root.getStage();
        if (stage == null) return false;
        var focus = stage.getKeyboardFocus();
        return focus instanceof TextField || focus instanceof VisTextField;
    }
}
