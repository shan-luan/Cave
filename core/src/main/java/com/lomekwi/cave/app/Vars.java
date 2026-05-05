package com.lomekwi.cave.app;


import com.google.common.eventbus.EventBus;

import games.spooky.gdx.nativefilechooser.NativeFileChooser;

public final class Vars {
    public static NativeFileChooser fileChooser;
    public static final EventBus appEventBus = new EventBus();
}
