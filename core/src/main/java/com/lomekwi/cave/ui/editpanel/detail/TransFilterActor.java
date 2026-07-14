package com.lomekwi.cave.ui.editpanel.detail;

import static com.lomekwi.cave.util.i18n.I18N.i18n;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.kotcrab.vis.ui.widget.VisCheckBox;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.spinner.SimpleFloatSpinnerModel;
import com.kotcrab.vis.ui.widget.spinner.Spinner;
import com.lomekwi.cave.app.App;
import com.lomekwi.cave.pipeline.image.TransFilter;
import com.lomekwi.cave.project.Project;
import com.lomekwi.cave.timeline.UndoManager;
import com.lomekwi.cave.timeline.playback.RefreshRequestEvent;

public class TransFilterActor extends FilterActor {
    private final SimpleFloatSpinnerModel dxModel, dyModel;
    private final SimpleFloatSpinnerModel sxModel, syModel;
    private final SimpleFloatSpinnerModel rotModel;
    private final VisCheckBox flipXBox, flipYBox;
    private boolean suppressRefresh;
    private long lastEditTime = -1;
    private UndoManager.TransFilterState undoOldState;

    public TransFilterActor(TransFilter filter) {
        super(filter.getName(), filter);

        dxModel = new SimpleFloatSpinnerModel(filter.dx(), -9999, 9999, 1, 1);
        dyModel = new SimpleFloatSpinnerModel(filter.dy(), -9999, 9999, 1, 1);
        sxModel = new SimpleFloatSpinnerModel(filter.scaleX(), 0.01f, 100, 0.01f, 2);
        syModel = new SimpleFloatSpinnerModel(filter.scaleY(), 0.01f, 100, 0.01f, 2);
        rotModel = new SimpleFloatSpinnerModel(filter.dRotation(), -9999, 9999, 1, 1);

        var dxSpinner = new Spinner("", dxModel);
        var dySpinner = new Spinner("", dyModel);
        var sxSpinner = new Spinner("", sxModel);
        var sySpinner = new Spinner("", syModel);
        var rotSpinner = new Spinner("", rotModel);
        flipXBox = new VisCheckBox("", filter.flipX());
        flipYBox = new VisCheckBox("", filter.flipY());

        ChangeListener updater = new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (suppressRefresh) return;
                if (lastEditTime == -1) {
                    undoOldState = new UndoManager.TransFilterState(
                        filter.dx(), filter.dy(),
                        filter.scaleX(), filter.scaleY(),
                        filter.dRotation(),
                        filter.flipX(), filter.flipY());
                }
                lastEditTime = System.nanoTime();
                if (actor instanceof Spinner s) {
                    SimpleFloatSpinnerModel m = (SimpleFloatSpinnerModel) s.getModel();
                    float v = m.getValue();
                    if (s == dxSpinner) filter.dx(v);
                    else if (s == dySpinner) filter.dy(v);
                    else if (s == sxSpinner) filter.scaleX(v);
                    else if (s == sySpinner) filter.scaleY(v);
                    else if (s == rotSpinner) filter.dRotation(v);
                } else if (actor instanceof VisCheckBox cb) {
                    if (cb == flipXBox) filter.flipX(cb.isChecked());
                    else if (cb == flipYBox) filter.flipY(cb.isChecked());
                }
                Project p = App.root.getFrontendProject();
                if (p != null) p.projEventBus.post(RefreshRequestEvent.INSTANCE);
            }
        };
        dxSpinner.addListener(updater);
        dySpinner.addListener(updater);
        sxSpinner.addListener(updater);
        sySpinner.addListener(updater);
        rotSpinner.addListener(updater);
        flipXBox.addListener(updater);
        flipYBox.addListener(updater);

        add(new VisLabel(i18n("位移 X"))).pad(4);
        add(dxSpinner).width(90).pad(4).row();
        add(new VisLabel(i18n("位移 Y"))).pad(4);
        add(dySpinner).width(90).pad(4).row();
        add(new VisLabel(i18n("缩放 X"))).pad(4);
        add(sxSpinner).width(90).pad(4).row();
        add(new VisLabel(i18n("缩放 Y"))).pad(4);
        add(sySpinner).width(90).pad(4).row();
        add(new VisLabel(i18n("旋转"))).pad(4);
        add(rotSpinner).width(90).pad(4).row();
        add(new VisLabel(i18n("水平翻转"))).pad(4);
        add(flipXBox).pad(4).row();
        add(new VisLabel(i18n("垂直翻转"))).pad(4);
        add(flipYBox).pad(4);
    }

    public void syncFromFilter() {
        suppressRefresh = true;
        TransFilter tf = (TransFilter) filter;
        dxModel.setValue(tf.dx());
        dyModel.setValue(tf.dy());
        sxModel.setValue(tf.scaleX());
        syModel.setValue(tf.scaleY());
        rotModel.setValue(tf.dRotation());
        flipXBox.setChecked(tf.flipX());
        flipYBox.setChecked(tf.flipY());
        suppressRefresh = false;
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        if (lastEditTime != -1 && System.nanoTime() - lastEditTime > 400_000_000L) {
            Project p = App.root.getFrontendProject();
            if (p != null && undoOldState != null) {
                TransFilter tf = (TransFilter) filter;
                UndoManager.TransFilterState newState = new UndoManager.TransFilterState(
                    tf.dx(), tf.dy(),
                    tf.scaleX(), tf.scaleY(),
                    tf.dRotation(),
                    tf.flipX(), tf.flipY());
                if (!undoOldState.equals(newState)) {
                    p.undoManager.record(new UndoManager.TransformFilterCommand(
                        tf, undoOldState, newState));
                }
            }
            lastEditTime = -1;
            undoOldState = null;
        }
    }
}
