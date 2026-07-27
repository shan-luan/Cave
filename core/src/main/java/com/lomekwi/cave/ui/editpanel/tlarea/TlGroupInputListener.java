package com.lomekwi.cave.ui.editpanel.tlarea;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;

import static com.badlogic.gdx.Input.Keys.*;

import com.lomekwi.cave.app.App;

public class TlGroupInputListener extends InputListener {

    private final TlGroup tlGroup;

    public TlGroupInputListener(TlGroup tlGroup) {
        this.tlGroup = tlGroup;
    }

    @Override
    public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
        if (button == Input.Buttons.LEFT && !tlGroup.marqueeActive) {
            tlGroup.clearSelection();
            tlGroup.playhead.seek(Math.max(tlGroup.xToAbsoluteTime(x), 0));
            return true;
        }
        if (button == Input.Buttons.RIGHT && event.getTarget() == event.getListenerActor()) {
            tlGroup.tlGroupMenu.setContext(Math.max(tlGroup.xToAbsoluteTime(x), 0));
            return true;
        }
        return false;
    }

    @Override
    public void touchDragged(InputEvent event, float x, float y, int pointer) {
        if (tlGroup.marqueeActive) return;
        tlGroup.playhead.seek(Math.max(tlGroup.xToAbsoluteTime(x), 0));
    }

    @Override
    public boolean scrolled(InputEvent event, float x, float y, float amountX, float amountY) {
        final Input ip = Gdx.input;

        if (ip.isKeyPressed(CONTROL_LEFT) && ip.isKeyPressed(SHIFT_LEFT)) {
            tlGroup.view.adjustTrackHeight(amountY * 10);

        } else if (ip.isKeyPressed(CONTROL_LEFT)) {
            tlGroup.view.scrollVertical(amountY * 10);

        } else if (ip.isKeyPressed(SHIFT_LEFT)) {
            tlGroup.view.scrollHorizontal(amountY * 30, tlGroup.getWidth());

        } else {
            if (!tlGroup.view.zoom(amountY, x / tlGroup.getWidth())) return true;
        }

        tlGroup.dirty = true;
        return true;
    }

    @Override
    public boolean keyDown(InputEvent event, int keycode) {
        if (App.shortcutManager.isActive(TlGroup.Actions.PLAY_PAUSE)) {
            tlGroup.playhead.setPlaying(!tlGroup.playhead.isPlaying());
            return true;
        }
        if (App.shortcutManager.isActive(TlGroup.Actions.SPLIT)) {
            tlGroup.dragHandler.splitAtCursor();
            return true;
        }
        if (App.shortcutManager.isActive(TlGroup.Actions.DELETE)) {
            tlGroup.dragHandler.deleteSelected();
            return true;
        }
        if (App.shortcutManager.isActive(TlGroup.Actions.GROUP)) {
            tlGroup.groupSelectedSegments();
            return true;
        }
        if (App.shortcutManager.isActive(TlGroup.Actions.PASTE)) {
            tlGroup.performPaste();
            return true;
        }
        return true;
    }
}
