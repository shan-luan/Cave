package com.lomekwi.cave.ui.editpanel.inspector;

import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.spinner.SimpleFloatSpinnerModel;
import com.kotcrab.vis.ui.widget.spinner.Spinner;
import com.lomekwi.cave.app.App;
import com.lomekwi.cave.pipeline.Filter;
import com.lomekwi.cave.pipeline.Node;
import com.lomekwi.cave.pipeline.NumInPort;
import com.lomekwi.cave.pipeline.Source;
import com.lomekwi.cave.project.Project;
import com.lomekwi.cave.timeline.UndoManager;
import com.lomekwi.cave.timeline.playback.RefreshRequestEvent;
import com.lomekwi.cave.ui.widget.Card;

/**
 * 过滤器节点卡：显示 filter 名称，可编辑其数值输入端口（默认值），支持删除。
 */
public final class FilterActor extends Card {
    private final Filter<?> filter;
    private final Source<?> source;
    private Runnable rebuildCallback;

    public FilterActor(Source<?> source, Filter<?> filter) {
        super(filter.getName());
        this.source = source;
        this.filter = filter;
        defaults().left();
        addCloseButton();
        for (Node.InPort<?> in : filter.getInPorts()) {
            if (in instanceof NumInPort numIn) {
                addNumRow(numIn);
            } else {
                add(new VisLabel("> " + in.getName())).pad(2).left().row();
            }
        }
    }

    private void addNumRow(NumInPort port) {
        double cur = port.getDefaultData() != null ? port.getDefaultData().getVal() : 0;
        SimpleFloatSpinnerModel model = new SimpleFloatSpinnerModel(
            (float) cur, -99999, 99999, 0.01f, 2);
        Spinner spinner = new Spinner("", model);
        spinner.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                double newVal = ((SimpleFloatSpinnerModel) spinner.getModel()).getValue();
                double oldVal = port.getDefaultData() != null ? port.getDefaultData().getVal() : 0;
                if (oldVal == newVal) return;
                Project p = App.root.getFrontendProject();
                if (p != null) {
                    p.undoManager.record(new UndoManager.NumPortValueCommand(port, oldVal, newVal));
                    p.projEventBus.post(RefreshRequestEvent.INSTANCE);
                }
                if (port.getDefaultData() != null) port.getDefaultData().setVal(newVal);
            }
        });
        add(new VisLabel(port.getName())).pad(2).left();
        add(spinner).width(90).pad(2).row();
    }

    @Override
    public void close() {
        int index = source.getFilters().indexOf(filter);
        if (index < 0) return;
        source.getFilters().remove(filter);
        Project p = App.root.getFrontendProject();
        if (p != null) {
            p.undoManager.record(new UndoManager.RemoveFilterCommand(source, filter, index));
            p.projEventBus.post(RefreshRequestEvent.INSTANCE);
        }
        remove();
        if (rebuildCallback != null) rebuildCallback.run();
    }

    public void setRebuildCallback(Runnable callback) {
        this.rebuildCallback = callback;
    }
}