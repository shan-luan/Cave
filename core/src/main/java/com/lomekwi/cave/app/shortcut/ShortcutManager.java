package com.lomekwi.cave.app.shortcut;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

import java.util.*;
import java.util.stream.Collectors;

public class ShortcutManager {
    private static final String PREFS_NAME = "cave-shortcuts";

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

    public void load() {
        Preferences prefs = Gdx.app.getPreferences(PREFS_NAME);
        for (ShortcutAction action : registeredActions) {
            String val = prefs.getString(action.toString(), null);
            if (val != null && !val.isEmpty()) {
                int[] keys = Arrays.stream(val.split(","))
                    .mapToInt(Integer::parseInt)
                    .toArray();
                register(action, keys);
            }
        }
    }

    public void persist() {
        Preferences prefs = Gdx.app.getPreferences(PREFS_NAME);
        prefs.clear();
        for (ShortcutAction action : registeredActions) {
            Collection<Integer> keys = actionToKeys.get(action);
            if (!keys.isEmpty()) {
                String val = keys.stream().map(String::valueOf).collect(Collectors.joining(","));
                prefs.putString(action.toString(), val);
            }
        }
        prefs.flush();
    }
}
