package com.lomekwi.cave.ui.editpanel.inspector;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.VisImageButton;
import com.kotcrab.vis.ui.widget.VisTable;
import com.lomekwi.cave.app.App;
import com.lomekwi.cave.pipeline.Modifier;
import com.lomekwi.cave.pipeline.Source;
import com.lomekwi.cave.project.Project;
import com.lomekwi.cave.timeline.UndoManager;
import com.lomekwi.cave.timeline.playback.RefreshRequestEvent;
import com.lomekwi.cave.ui.widget.Card;

import java.util.List;

public class ModifierActor extends Card {
    private final Modifier<?> modifier;
    private final Source<?> source;
    private final ParamBinder binder;
    private Runnable rebuildCallback;
    private boolean dragging;
    private float dragStageY, dragWindowY;

    public ModifierActor(Source<?> source, Modifier<?> modifier) {
        super(modifier.getName());
        this.source = source;
        this.modifier = modifier;
        align(Align.top | Align.left);
        defaults().left();
        addMoveButton(true);
        addMoveButton(false);
        addCloseButton();
        addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                if (button == 0 && y >= getHeight() - getPadTop()) {
                    dragging = true;
                    dragStageY = event.getStageY();
                    dragWindowY = getY();
                    event.cancel();
                    return true;
                }
                return false;
            }

            @Override
            public void touchDragged(InputEvent event, float x, float y, int pointer) {
                if (!dragging) return;
                event.cancel();
                setY(dragWindowY + (event.getStageY() - dragStageY));
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                if (!dragging) return;
                dragging = false;
                doReorder();
                if (rebuildCallback != null) {
                    rebuildCallback.run();
                }
            }
        });

        binder = new ParamBinder(new ParamBinder.Owner() {
            @Override
            public Source<?> source() {
                return source;
            }

            @Override
            public void invalidateDetailActor() {
                modifier.invalidateDetailActor();
            }
        });
        binder.bind(this, modifier.getParams());
    }

    /** 模型被外部（gizmo、undo 等）修改后，把当前值回显到 widget。 */
    public void syncFromModel() {
        binder.syncFromModel();
    }

    public void setRebuildCallback(Runnable callback) {
        this.rebuildCallback = callback;
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        binder.tick();
    }

    private void addMoveButton(boolean up) {
        VisImageButton.VisImageButtonStyle style = new VisImageButton.VisImageButtonStyle(
            VisUI.getSkin().get("close-window", VisImageButton.VisImageButtonStyle.class));
        style.imageUp = VisUI.getSkin().getDrawable(up ? "select-up" : "select-down");
        VisImageButton button = new VisImageButton(style);
        getTitleTable().add(button);
        button.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeListener.ChangeEvent event, Actor actor) {
                move(up);
            }
        });
        button.addListener(new ClickListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                event.cancel();
                return true;
            }
        });
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void move(boolean up) {
        if (source == null) return;
        List modifiers = source.getModifiers();
        int index = modifiers.indexOf(modifier);
        if (index < 0) return;
        int target = up ? index - 1 : index + 1;
        if (target < 0 || target >= modifiers.size()) return;
        modifiers.remove(index);
        modifiers.add(target, modifier);
        Project p = App.root.getFrontendProject();
        if (p != null) {
            p.undoManager.record(new UndoManager.ReorderModifierCommand(source, modifier, index, target));
            p.projEventBus.post(RefreshRequestEvent.INSTANCE);
        }
        if (rebuildCallback != null) {
            rebuildCallback.run();
        }
    }

    @Override
    public void close() {
        modifier.invalidateDetailActor();
        int index = source.getModifiers().indexOf(modifier);
        if (index < 0) return;
        source.getModifiers().remove(modifier);
        Project p = App.root.getFrontendProject();
        if (p != null) {
            p.undoManager.record(new UndoManager.RemoveModifierCommand(source, modifier, index));
            p.projEventBus.post(RefreshRequestEvent.INSTANCE);
        }
        remove();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void doReorder() {
        if (source == null) return;
        Actor p = getParent();
        if (!(p instanceof VisTable content)) return;

        List modifiers = source.getModifiers();
        int myIndex = modifiers.indexOf(modifier);
        if (myIndex < 0) return;

        float myCenterY = getY() + getHeight() / 2;
        int target = 0;
        for (Actor child : content.getChildren()) {
            if (!(child instanceof ModifierActor) || child == this) continue;
            if (child.getY() + child.getHeight() / 2 > myCenterY) target++;
        }

        if (target == myIndex) return;

        modifiers.remove(myIndex);
        modifiers.add(target, modifier);

        Project pj = App.root.getFrontendProject();
        if (pj != null) {
            pj.undoManager.record(new UndoManager.ReorderModifierCommand(source, modifier, myIndex, target));
            pj.projEventBus.post(RefreshRequestEvent.INSTANCE);
        }
    }
}
