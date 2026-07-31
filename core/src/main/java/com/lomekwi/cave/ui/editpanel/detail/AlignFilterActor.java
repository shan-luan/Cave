package com.lomekwi.cave.ui.editpanel.detail;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.ButtonGroup;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.kotcrab.vis.ui.widget.VisTextButton;
import com.lomekwi.cave.app.App;
import com.lomekwi.cave.pipeline.image.AlignFilter;
import com.lomekwi.cave.project.Project;
import com.lomekwi.cave.timeline.UndoManager;
import com.lomekwi.cave.timeline.playback.RefreshRequestEvent;

public class AlignFilterActor extends FilterActor {
    private static final String[][] LABELS = {
        {"左上", "中上", "右上"},
        {"左中", "正中", "右中"},
        {"左下", "中下", "右下"},
    };

    private final AlignFilter alignFilter;
    private final VisTextButton[][] buttons = new VisTextButton[3][3];
    private boolean suppressRefresh;

    public AlignFilterActor(AlignFilter filter) {
        super(filter.getName(), filter);
        this.alignFilter = filter;

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
                        AlignFilter.HAlign newH = AlignFilter.HAlign.values()[hh];
                        AlignFilter.VAlign newV = AlignFilter.VAlign.values()[hv];
                        Project p = App.root.getFrontendProject();
                        if (p != null) {
                            p.undoManager.record(new UndoManager.AlignFilterCommand(
                                alignFilter, alignFilter.getHAlign(), alignFilter.getVAlign(), newH, newV));
                        }
                        alignFilter.setAlign(newH, newV);
                        if (p != null) p.projEventBus.post(RefreshRequestEvent.INSTANCE);
                    }
                });
                add(b).width(60).height(28).pad(2);
                buttons[v][h] = b;
            }
            row();
        }

        syncFromFilter();
    }

    public void syncFromFilter() {
        suppressRefresh = true;
        for (int v = 0; v < 3; v++) {
            for (int h = 0; h < 3; h++) {
                buttons[v][h].setChecked(
                    alignFilter.getHAlign() == AlignFilter.HAlign.values()[h]
                        && alignFilter.getVAlign() == AlignFilter.VAlign.values()[v]);
            }
        }
        suppressRefresh = false;
    }
}
