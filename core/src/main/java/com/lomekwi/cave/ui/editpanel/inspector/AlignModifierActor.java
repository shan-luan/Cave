package com.lomekwi.cave.ui.editpanel.inspector;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.ButtonGroup;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.kotcrab.vis.ui.widget.VisTextButton;
import com.lomekwi.cave.app.App;
import com.lomekwi.cave.pipeline.image.AlignModifier;
import com.lomekwi.cave.project.Project;
import com.lomekwi.cave.timeline.UndoManager;
import com.lomekwi.cave.timeline.playback.RefreshRequestEvent;

public class AlignModifierActor extends ModifierActor {
    private static final String[][] LABELS = {
        {"左上", "中上", "右上"},
        {"左中", "正中", "右中"},
        {"左下", "中下", "右下"},
    };

    private final AlignModifier alignModifier;
    private final VisTextButton[][] buttons = new VisTextButton[3][3];
    private boolean suppressRefresh;

    public AlignModifierActor(AlignModifier modifier) {
        super(modifier.getName(), modifier);
        this.alignModifier = modifier;

        ButtonGroup<VisTextButton> group = new ButtonGroup<>();
        for (int v = 0; v < 3; v++) {
            for (int h = 0; h < 3; h++) {
                int hv = v;
                int hh = h;
                VisTextButton b = new VisTextButton(LABELS[v][h], "toggle");
                group.add(b);
                b.addListener(new ChangeListener() {
                    @Override
                    public void changed(ChangeEvent event, Actor actor) {
                        if (suppressRefresh) return;
                        if (!b.isChecked()) return;
                        AlignModifier.HAlign newH = AlignModifier.HAlign.values()[hh];
                        AlignModifier.VAlign newV = AlignModifier.VAlign.values()[hv];
                        Project p = App.root.getFrontendProject();
                        if (p != null) {
                            p.undoManager.record(new UndoManager.AlignModifierCommand(
                                alignModifier, alignModifier.getHAlign(), alignModifier.getVAlign(), newH, newV));
                        }
                        alignModifier.setAlign(newH, newV);
                        if (p != null) p.projEventBus.post(RefreshRequestEvent.INSTANCE);
                    }
                });
                add(b).width(60).height(28).pad(2);
                buttons[v][h] = b;
            }
            row();
        }

        syncFromModifier();
    }

    public void syncFromModifier() {
        suppressRefresh = true;
        for (int v = 0; v < 3; v++) {
            for (int h = 0; h < 3; h++) {
                buttons[v][h].setChecked(
                    alignModifier.getHAlign() == AlignModifier.HAlign.values()[h]
                        && alignModifier.getVAlign() == AlignModifier.VAlign.values()[v]);
            }
        }
        suppressRefresh = false;
    }
}
