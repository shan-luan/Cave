package com.lomekwi.cave.ui.editpanel.settings;

import static com.lomekwi.cave.util.i18n.I18N.i18n;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisScrollPane;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisTextButton;
import com.lomekwi.cave.app.App;
import com.lomekwi.cave.app.shortcut.ShortcutAction;

import java.util.*;

public class ShortcutKeysTable extends EntryTable {

    private final VisTable listTable = new VisTable();
    private ShortcutAction recordingAction = null;
    private VisTextButton recordingButton = null;

    private final InputProcessor recordingProcessor = new InputProcessor() {
        @Override
        public boolean keyDown(int keycode) {
            if (recordingAction == null) return false;
            if (keycode == Input.Keys.ESCAPE) {
                finishRecording(false);
                return true;
            }
            if (isModifier(keycode)) return true;

            List<Integer> keys = new ArrayList<>();
            if (Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT) || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT))
                keys.add(Input.Keys.CONTROL_LEFT);
            if (Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT))
                keys.add(Input.Keys.SHIFT_LEFT);
            if (Gdx.input.isKeyPressed(Input.Keys.ALT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.ALT_RIGHT))
                keys.add(Input.Keys.ALT_LEFT);
            keys.add(keycode);

            App.shortcutManager.register(recordingAction, keys.stream().mapToInt(i -> i).toArray());
            App.shortcutManager.persist();
            finishRecording(true);
            return true;
        }

        @Override public boolean keyUp(int keycode) { return false; }
        @Override public boolean keyTyped(char character) { return false; }
        @Override public boolean touchDown(int screenX, int screenY, int pointer, int button) { return false; }
        @Override public boolean touchUp(int screenX, int screenY, int pointer, int button) { return false; }
        @Override public boolean touchCancelled(int screenX, int screenY, int pointer, int button) { return false; }
        @Override public boolean touchDragged(int screenX, int screenY, int pointer) { return false; }
        @Override public boolean mouseMoved(int screenX, int screenY) { return false; }
        @Override public boolean scrolled(float amountX, float amountY) { return false; }
    };

    public ShortcutKeysTable() {
        rebuild();
    }

    private void rebuild() {
        clear();
        top().left();

        add(new VisLabel(i18n("快捷键设置"))).pad(10).row();

        VisTable header = new VisTable();
        header.add(new VisLabel(i18n("动作"))).pad(5).width(200);
        header.add(new VisLabel(i18n("快捷键"))).pad(5).width(280);
        listTable.clear();
        listTable.top().left();
        listTable.add(header).fillX().row();

        for (ShortcutAction action : App.shortcutManager.getAllActions()) {
            VisTable row = new VisTable();
            row.add(new VisLabel(action.displayName())).pad(5).width(200);

            VisTextButton keyBtn = new VisTextButton(keyDisplay(action));
            keyBtn.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    startRecording(action, keyBtn);
                }
            });
            row.add(keyBtn).pad(5).width(280);

            VisTextButton resetBtn = new VisTextButton(i18n("重置"));
            resetBtn.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    if (recordingAction != null) stopRecording();
                    App.shortcutManager.resetToDefault(action);
                    App.shortcutManager.persist();
                    rebuild();
                }
            });
            row.add(resetBtn).pad(5).width(60);

            listTable.add(row).fillX().padBottom(2).row();
        }

        VisScrollPane scrollPane = new VisScrollPane(listTable);
        scrollPane.setForceScroll(false, true);
        scrollPane.setFadeScrollBars(false);
        add(scrollPane).grow();
    }

    private void startRecording(ShortcutAction action, VisTextButton btn) {
        if (recordingAction != null) {
            recordingButton.setText(keyDisplay(recordingAction));
            stopRecording();
        }
        recordingAction = action;
        recordingButton = btn;
        btn.setText(i18n("按下新快捷键..."));
        InputProcessor current = Gdx.input.getInputProcessor();
        if (current instanceof InputMultiplexer) {
            ((InputMultiplexer) current).addProcessor(0, recordingProcessor);
        }
    }

    private void stopRecording() {
        recordingAction = null;
        recordingButton = null;
        InputProcessor current = Gdx.input.getInputProcessor();
        if (current instanceof InputMultiplexer) {
            ((InputMultiplexer) current).removeProcessor(recordingProcessor);
        }
    }

    private void finishRecording(boolean apply) {
        stopRecording();
        rebuild();
    }

    private String keyDisplay(ShortcutAction action) {
        Collection<Integer> keys = App.shortcutManager.getKeys(action);
        if (keys.isEmpty()) return i18n("未设置");
        List<Integer> list = new ArrayList<>(keys);
        list.sort((a, b) -> {
            int aMod = isModifier(a) ? 0 : 1;
            int bMod = isModifier(b) ? 0 : 1;
            if (aMod != bMod) return aMod - bMod;
            return 0;
        });
        StringBuilder sb = new StringBuilder();
        for (int k : list) {
            if (sb.length() > 0) sb.append(" + ");
            sb.append(keyName(k));
        }
        return sb.toString();
    }

    private static boolean isModifier(int keycode) {
        return keycode == Input.Keys.CONTROL_LEFT || keycode == Input.Keys.CONTROL_RIGHT
            || keycode == Input.Keys.SHIFT_LEFT || keycode == Input.Keys.SHIFT_RIGHT
            || keycode == Input.Keys.ALT_LEFT || keycode == Input.Keys.ALT_RIGHT;
    }

    private static String keyName(int keycode) {
        if (keycode >= Input.Keys.A && keycode <= Input.Keys.Z)
            return String.valueOf((char) ('A' + (keycode - Input.Keys.A)));
        if (keycode >= Input.Keys.NUM_0 && keycode <= Input.Keys.NUM_9)
            return String.valueOf((char) ('0' + (keycode - Input.Keys.NUM_0)));
        return switch (keycode) {
            case Input.Keys.CONTROL_LEFT, Input.Keys.CONTROL_RIGHT -> "Ctrl";
            case Input.Keys.SHIFT_LEFT, Input.Keys.SHIFT_RIGHT -> "Shift";
            case Input.Keys.ALT_LEFT, Input.Keys.ALT_RIGHT -> "Alt";
            case Input.Keys.SPACE -> i18n("空格");
            case Input.Keys.DEL, Input.Keys.FORWARD_DEL -> "Del";
            case Input.Keys.ESCAPE -> "Esc";
            case Input.Keys.TAB -> "Tab";
            case Input.Keys.ENTER -> "Enter";
            case Input.Keys.UP -> i18n("上");
            case Input.Keys.DOWN -> i18n("下");
            case Input.Keys.LEFT -> i18n("左");
            case Input.Keys.RIGHT -> i18n("右");
            case Input.Keys.F1 -> "F1";
            case Input.Keys.F2 -> "F2";
            case Input.Keys.F3 -> "F3";
            case Input.Keys.F4 -> "F4";
            case Input.Keys.F5 -> "F5";
            case Input.Keys.F6 -> "F6";
            case Input.Keys.F7 -> "F7";
            case Input.Keys.F8 -> "F8";
            case Input.Keys.F9 -> "F9";
            case Input.Keys.F10 -> "F10";
            case Input.Keys.F11 -> "F11";
            case Input.Keys.F12 -> "F12";
            case Input.Keys.MINUS -> "-";
            case Input.Keys.EQUALS -> "=";
            case Input.Keys.COMMA -> ",";
            case Input.Keys.PERIOD -> ".";
            case Input.Keys.SEMICOLON -> ";";
            case Input.Keys.APOSTROPHE -> "'";
            case Input.Keys.SLASH -> "/";
            case Input.Keys.LEFT_BRACKET -> "[";
            case Input.Keys.RIGHT_BRACKET -> "]";
            case Input.Keys.BACKSLASH -> "\\";
            case Input.Keys.GRAVE -> "`";
            default -> "Key#" + keycode;
        };
    }

    @Override
    public String getName() {
        return i18n("快捷键");
    }
}
