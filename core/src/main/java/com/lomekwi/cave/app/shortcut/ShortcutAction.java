package com.lomekwi.cave.app.shortcut;

public interface ShortcutAction {
    default String displayName() {
        return toString();
    }

    default int[] defaultKeys() {
        return new int[0];
    }
}
