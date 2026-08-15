package com.lomekwi.cave.ui.editpanel.inspector;

import static com.lomekwi.cave.util.i18n.I18N.i18n;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.ui.Cell;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.google.common.eventbus.Subscribe;
import com.lomekwi.cave.app.App;
import com.kotcrab.vis.ui.widget.MenuItem;
import com.kotcrab.vis.ui.widget.PopupMenu;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisScrollPane;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisTextButton;
import com.kotcrab.vis.ui.widget.VisTextField;
import com.lomekwi.cave.pipeline.Modifier;
import com.lomekwi.cave.pipeline.Source;
import com.lomekwi.cave.timeline.Segment;
import com.lomekwi.cave.timeline.SegmentSet;
import com.lomekwi.cave.timeline.SegmentSetSelectedEvent;
import com.lomekwi.cave.timeline.UndoManager;
import com.lomekwi.cave.timeline.SegmentSelectedEvent;

import java.util.ArrayList;
import java.util.List;

public class Inspector extends VisTable {
    private final VisTable content;
    private Segment currentSeg;
    private SegmentSet currentSegSet;

    public Inspector() {
        content = new VisTable();
        VisScrollPane scrollPane = new VisScrollPane(content);
        add(scrollPane).grow();
        showEmpty();
    }

    @Subscribe
    public void onSegmentSelected(SegmentSelectedEvent e) {
        int count = e.selectedCount();
        if (count == 0) {
            showEmpty();
        } else if (count == 1 && e.segment() != null) {
            showInfo(e.segment());
        }
    }

    @Subscribe
    public void onSegmentSetSelected(SegmentSetSelectedEvent e) {
        if (e.selectedCount() > 1) {
            showMultiInfo(e.set());
        }
    }

    public void rebuildContent() {
        if (currentSegSet != null) {
            showMultiInfo(currentSegSet);
        } else if (currentSeg != null) {
            showInfo(currentSeg);
        }
    }

    private void showEmpty() {
        currentSeg = null;
        currentSegSet = null;
        content.clear();
        content.setFillParent(true);
        content.add(new VisLabel(i18n("未选择片段"))).expand().center();
    }

    private void showMultiInfo(SegmentSet set) {
        currentSeg = null;
        currentSegSet = set;
        content.clear();
        content.setFillParent(false);
        content.top();
        List<Segment> segs = new ArrayList<>(set.getSegments());
        segs.sort(null);
        boolean first = true;
        for (Segment seg : segs) {
            if (!first) {
                content.row();
            }
            first = false;
            appendSegmentInfo(seg);
        }
        collectInto(content);
    }

    private void showInfo(Segment seg) {
        if (seg == null) return;
        currentSeg = seg;
        currentSegSet = null;
        content.clear();
        content.setFillParent(false);
        content.top();
        appendSegmentInfo(seg);
        collectInto(content);
    }

    private void collectInto(Actor actor) {
        if (actor instanceof TextField || actor instanceof VisTextField) {
            return;
        }
        if (actor instanceof Table table) {
            for (Cell cell : table.getCells()) {
                Object cellActor = cell.getActor();
                if (cellActor instanceof Actor act) {
                    collectInto(act);
                }
            }
        } else if (actor instanceof Group group) {
            for (Actor child : group.getChildren()) {
                collectInto(child);
            }
        }
    }

    private void appendSegmentInfo(Segment seg) {
        Source<?> source = seg.getSource();
        content.add(source.getSourceActor()).growX().pad(4).row();
        for (Modifier<?> modifier : source.getModifiers()) {
            var actor = modifier.getActor();
            if (actor != null) {
                if (actor instanceof ModifierActor ma) {
                    ma.setSource(source);
                    ma.setRebuildCallback(this::rebuildContent);
                }
                content.add(actor).growX().pad(4).row();
            }
        }
        VisTextButton addBtn = new VisTextButton(i18n("+添加修改器"));
        PopupMenu modifierMenu = new PopupMenu();
        int compatibleCount = App.modifierRegistry.getCompatibleCount(source);
        for (int fi = 0; fi < compatibleCount; fi++) {
            final int idx = fi;
            Modifier<?> newModifier = App.modifierRegistry.createCompatible(source, idx);
            modifierMenu.addItem(new MenuItem(newModifier.getName(), new ChangeListener() {
                @Override
                public void changed(ChangeListener.ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                    source.getModifiers().add((Modifier) newModifier);
                    var p = App.root.getFrontendProject();
                    if (p != null) p.undoManager.record(new UndoManager.AddModifierCommand(source, newModifier));
                    rebuildContent();
                }
            }));
        }
        addBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeListener.ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                modifierMenu.showMenu(getStage(), addBtn);
            }
        });
        content.add(addBtn).pad(4).left();
    }
}
