package com.lomekwi.cave.app.shortcut;

import com.badlogic.gdx.Gdx;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

import java.util.*;

public class ShortcutManager {
    private final Multimap<ShortcutAction, Integer> actionToKeys = ArrayListMultimap.create();
    private final Set<ShortcutAction> registeredActions = new LinkedHashSet<>();

    public void register(ShortcutAction action, int... keyCode) {
        actionToKeys.removeAll(action);
        for (int k : keyCode) {
            actionToKeys.put(action, k);
        }
        registeredActions.add(action);
    }

    public void resetToDefault(ShortcutAction action) {
        register(action, action.defaultKeys());
    }

    public Collection<Integer> getKeys(ShortcutAction action) {
        return actionToKeys.get(action);
    }

    public Set<ShortcutAction> getAllActions() {
        return Collections.unmodifiableSet(registeredActions);
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
