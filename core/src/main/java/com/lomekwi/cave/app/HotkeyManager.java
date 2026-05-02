package com.lomekwi.cave.app;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;

public class HotkeyManager extends InputAdapter {
    private static HotkeyManager instance;
    private final Map<KeyCombo, Callable<Boolean>> hotkeys = new HashMap<>();
    private final BiMap<KeyCombo, String> hotkeyNames = HashBiMap.create();

    private HotkeyManager() {
        
    }

    public static HotkeyManager getInstance() {
        if (instance == null) {
            instance = new HotkeyManager();
        }
        return instance;
    }

    public void registerHotkey(KeyCombo combo, Callable<Boolean> action) {
        hotkeys.put(combo, action);
    }

    public void registerHotkey(KeyCombo combo, Callable<Boolean> action, String name) {
        hotkeys.put(combo, action);
        hotkeyNames.put(combo, name);
    }

    public void registerHotkey(int keycode, Callable<Boolean> action) {
        registerHotkey(new KeyCombo(false, false, keycode), action);
    }

    public void unregisterHotkey(KeyCombo combo) {
        hotkeys.remove(combo);
        hotkeyNames.remove(combo);
    }

    public String getHotkeyName(KeyCombo combo) {
        return hotkeyNames.get(combo);
    }

    public KeyCombo getHotkeyCombo(String name) {
        return hotkeyNames.inverse().get(name);
    }

    public Map<KeyCombo, String> getAllHotkeyNames() {
        return new HashMap<>(hotkeyNames);
    }

    public BiMap<KeyCombo, String> getHotkeyNamesBiMap() {
        return HashBiMap.create(hotkeyNames);
    }

    @Override
    public boolean keyDown(int keycode) {
        KeyCombo combo = new KeyCombo(
            Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT),
            Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT),
            keycode
        );
        Callable<Boolean> action = hotkeys.get(combo);
        if (action != null) {
            try {
                return action.call();
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        }
        return false;
    }

    public static class KeyCombo {
        public final boolean ctrl;
        public final boolean shift;
        public final int keycode;

        public KeyCombo(boolean ctrl, boolean shift, int keycode) {
            this.ctrl = ctrl;
            this.shift = shift;
            this.keycode = keycode;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof KeyCombo)) return false;
            KeyCombo other = (KeyCombo) o;
            return ctrl == other.ctrl && shift == other.shift && keycode == other.keycode;
        }

        @Override
        public int hashCode() {
            return Objects.hash(ctrl, shift, keycode);
        }
    }
}
