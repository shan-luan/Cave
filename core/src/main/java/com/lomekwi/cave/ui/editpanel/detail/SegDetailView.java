package com.lomekwi.cave.ui.editpanel.detail;

import static com.lomekwi.cave.util.i18n.I18N.i18n;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.Cell;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.ui.Widget;
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
import com.lomekwi.cave.pipeline.Filter;
import com.lomekwi.cave.pipeline.FilterRegistry;
import com.lomekwi.cave.pipeline.Source;
import com.lomekwi.cave.timeline.Segment;
import com.lomekwi.cave.timeline.SegmentSet;
import com.lomekwi.cave.timeline.SegmentSetSelectedEvent;
import com.lomekwi.cave.timeline.UndoManager;
import com.lomekwi.cave.timeline.SegmentSelectedEvent;

import java.util.ArrayList;
import java.util.List;

public class SegDetailView extends VisTable {
    private final VisTable content;
    private Segment currentSeg;
    private SegmentSet currentSegSet;

    /**
     * 回车完成当前输入并把控制权移交给下一个输入框，完全等同于 Tab/Shift+Tab。
     * 直接委托 VisTextField.next()（与 VisUI 内部 Tab 相同的全舞台空间搜索 + 环绕）。
     */
    private final InputListener enterNav = new InputListener() {
        @Override
        public boolean keyTyped(InputEvent event, char character) {
            if (character != '\n' && character != '\r') return false;
            boolean back = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)
                || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT);
            Actor self = event.getListenerActor();
            if (self instanceof VisTextField vis) {
                vis.next(back);
            } else if (self instanceof TextField tf) {
                tf.next(back);
            } else {
                return false;
            }
            return true;
        }
    };

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
            Widget w = (Widget) actor;
            if (!w.getListeners().contains(enterNav, true)) {
                w.addListener(enterNav);
            }
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
                    rebuildContent();
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
