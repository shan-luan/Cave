package com.lomekwi.cave.app.shortcut;

import com.badlogic.gdx.Gdx;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

public class ShortcutManager {
    private final Multimap<ShortcutAction, Integer> actionToKeys = ArrayListMultimap.create();
    public void register(ShortcutAction action, int... keyCode) {
        actionToKeys.removeAll(action);
        for (int k : keyCode) {
            actionToKeys.put(action, k);
        }
    }
    public boolean isActive(ShortcutAction action) {
        var keys = actionToKeys.get(action);

        if (keys.isEmpty()) return false;

        for (int key : keys) {
            if (!Gdx.input.isKeyPressed(key)) {
                return false;
            }
        }

        return true;
    }
}
