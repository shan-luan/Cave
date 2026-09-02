package com.lomekwi.cave.ui.widget;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.spinner.SimpleFloatSpinnerModel;
import com.kotcrab.vis.ui.widget.spinner.Spinner;
import com.lomekwi.cave.app.App;
import com.lomekwi.cave.pipeline.Node;
import com.lomekwi.cave.pipeline.Source;
import com.lomekwi.cave.pipeline.num.NumFrame;
import com.lomekwi.cave.project.Project;
import com.lomekwi.cave.timeline.UndoManager;
import com.lomekwi.cave.timeline.playback.RefreshRequestEvent;

/**
 * NumFrame 输入端口编辑 widget：Spinner 行。直接持有端口模型，修改写默认值记 undo，
 * 并在自身的 act() 中把模型值回显到 widget（undo、gizmo 等外部修改后同步）。
 */
public final class NumPortEditor extends VisTable {
    private final Node.InPort<?> port;
    private final SimpleFloatSpinnerModel model;
    private final Spinner spinner;

    public NumPortEditor(Node.InPort<?> port, Source<?> source) {
        this.port = port;
        NumFrame defaultData = (NumFrame) port.getDefaultData();
        double cur = defaultData != null ? defaultData.getVal() : 0;
        model = new SimpleFloatSpinnerModel((float) cur, -99999, 99999, 1f, 2);
        spinner = new Spinner("", model);
        spinner.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                double newVal = model.getValue();
                double oldVal = defaultData != null ? defaultData.getVal() : 0;
                if (oldVal == newVal) return;
                Project p = App.root.getFrontendProject();
                if (p != null) {
                    p.undoManager.record(new UndoManager.NumPortValueCommand(port, source, oldVal, newVal));
                    p.projEventBus.post(RefreshRequestEvent.INSTANCE);
                }
                if (defaultData != null) defaultData.setVal(newVal);
            }
        });
        align(Align.topLeft);
        defaults().left();
        add(new VisLabel(port.getName())).pad(2);
        add(spinner).width(90).pad(2);
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        // 用户正在输入时不覆盖，避免打断编辑
        if (spinner.getTextField().hasKeyboardFocus()) return;
        NumFrame data = (NumFrame) port.getDefaultData();
        double modelVal = data != null ? data.getVal() : 0;
        float current = model.getValue();
        if (Math.abs(current - modelVal) > 0.005f) {
            model.setValue((float) modelVal, false);
            spinner.getTextField().setText(
                String.format(java.util.Locale.US, "%.2f", modelVal));
        }
    }
}