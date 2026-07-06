package com.lomekwi.cave.ui.editpanel.detail;

import static com.lomekwi.cave.util.i18n.I18N.i18n;

import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.google.common.eventbus.Subscribe;
import com.lomekwi.cave.app.App;
import com.kotcrab.vis.ui.widget.MenuItem;
import com.kotcrab.vis.ui.widget.PopupMenu;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisScrollPane;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisTextButton;
import com.lomekwi.cave.pipeline.Filter;
import com.lomekwi.cave.pipeline.FilterRegistry;
import com.lomekwi.cave.pipeline.Source;
import com.lomekwi.cave.timeline.Segment;
import com.lomekwi.cave.timeline.UndoManager;
import com.lomekwi.cave.timeline.SegmentSelectedEvent;

public class SegDetailView extends VisTable {
    private final VisTable content;
    private Segment currentSeg;

    public SegDetailView() {
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
        } else if (count > 1) {
            showMulti(count);
        } else {
            showInfo(e.segment());
        }
    }

    public void rebuildContent() {
        if (currentSeg != null) {
            showInfo(currentSeg);
        }
    }

    private void showEmpty() {
        currentSeg = null;
        content.clear();
        content.setFillParent(true);
        content.add(new VisLabel(i18n("未选择片段"))).expand().center();
    }

    private void showMulti(int count) {
        currentSeg = null;
        content.clear();
        content.setFillParent(true);
        content.add(new VisLabel(i18n("已选中") + count + i18n("个片段"))).expand().center();
    }

    private void showInfo(Segment seg) {
        if (seg == null) return;
        currentSeg = seg;
        content.clear();
        content.setFillParent(false);
        content.top();
        Source<?> source = seg.getSource();
        content.add(source.getDetailActor()).growX().pad(4).row();
        for (Filter<?> filter : source.getFilters()) {
            var actor = filter.getActor();
            if (actor != null) {
                if (actor instanceof FilterActor fa) {
                    fa.setSource(source);
                    fa.setRebuildCallback(this::rebuildContent);
                }
                content.add(actor).growX().pad(4).row();
            }
        }
        VisTextButton addBtn = new VisTextButton(i18n("+添加滤镜"));
        PopupMenu filterMenu = new PopupMenu();
        int compatibleCount = FilterRegistry.getCompatibleCount(source);
        for (int fi = 0; fi < compatibleCount; fi++) {
            final int idx = fi;
            filterMenu.addItem(new MenuItem(FilterRegistry.getCompatibleName(source, fi), new ChangeListener() {
                @Override
                public void changed(ChangeListener.ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                    Filter<?> newFilter = FilterRegistry.createCompatible(source, idx);
                    source.getFilters().add((Filter) newFilter);
                    var p = App.root.getFrontendProject();
                    if (p != null) p.undoManager.record(new UndoManager.AddFilterCommand(source, newFilter));
                    showInfo(seg);
                }
            }));
        }
        addBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeListener.ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                filterMenu.showMenu(getStage(), addBtn);
            }
        });
        content.add(addBtn).pad(4).left();
    }
}
