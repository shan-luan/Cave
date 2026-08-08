package com.lomekwi.cave.ui.editpanel.detail;

import static com.lomekwi.cave.util.i18n.I18N.i18n;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.kotcrab.vis.ui.widget.VisCheckBox;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.spinner.SimpleFloatSpinnerModel;
import com.kotcrab.vis.ui.widget.spinner.Spinner;
import com.lomekwi.cave.app.App;
import com.lomekwi.cave.pipeline.image.TransModifier;
import com.lomekwi.cave.project.Project;
import com.lomekwi.cave.timeline.UndoManager;
import com.lomekwi.cave.timeline.playback.RefreshRequestEvent;

public class TransModifierActor extends ModifierActor {
    private final SimpleFloatSpinnerModel dxModel, dyModel;
    private final SimpleFloatSpinnerModel sxModel, syModel;
    private final SimpleFloatSpinnerModel rotModel;
    private final VisCheckBox flipXBox, flipYBox;
    private boolean suppressRefresh;
    private long lastEditTime = -1;
    private UndoManager.TransModifierState undoOldState;

    public TransModifierActor(TransModifier modifier) {
        super(modifier.getName(), modifier);

        dxModel = new SimpleFloatSpinnerModel(modifier.dx.getFloat(), -9999, 9999, 1, 1);
        dyModel = new SimpleFloatSpinnerModel(modifier.dy.getFloat(), -9999, 9999, 1, 1);
        sxModel = new SimpleFloatSpinnerModel(modifier.scaleX.getFloat(), 0.01f, 100, 0.01f, 2);
        syModel = new SimpleFloatSpinnerModel(modifier.scaleY.getFloat(), 0.01f, 100, 0.01f, 2);
        rotModel = new SimpleFloatSpinnerModel(modifier.dRotation.getFloat(), -9999, 9999, 1, 1);

        var dxSpinner = new Spinner("", dxModel);
        var dySpinner = new Spinner("", dyModel);
        var sxSpinner = new Spinner("", sxModel);
        var sySpinner = new Spinner("", syModel);
        var rotSpinner = new Spinner("", rotModel);
        flipXBox = new VisCheckBox("", modifier.flipX());
        flipYBox = new VisCheckBox("", modifier.flipY());

        ChangeListener updater = new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (suppressRefresh) return;
                if (lastEditTime == -1) {
                    undoOldState = new UndoManager.TransModifierState(
                        modifier.dx.getFloat(), modifier.dy.getFloat(),
                        modifier.scaleX.getFloat(), modifier.scaleY.getFloat(),
                        modifier.dRotation.getFloat(),
                        modifier.flipX(), modifier.flipY());
                }
                lastEditTime = System.nanoTime();
                if (actor instanceof Spinner s) {
                    SimpleFloatSpinnerModel m = (SimpleFloatSpinnerModel) s.getModel();
                    float v = m.getValue();
                    if (s == dxSpinner) modifier.dx.set(v);
                    else if (s == dySpinner) modifier.dy.set(v);
                    else if (s == sxSpinner) modifier.scaleX.set(v);
                    else if (s == sySpinner) modifier.scaleY.set(v);
                    else if (s == rotSpinner) modifier.dRotation.set(v);
                } else if (actor instanceof VisCheckBox cb) {
                    if (cb == flipXBox) modifier.flipX(cb.isChecked());
                    else if (cb == flipYBox) modifier.flipY(cb.isChecked());
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

    public void syncFromModifier() {
        suppressRefresh = true;
        TransModifier tf = (TransModifier) modifier;
        dxModel.setValue(tf.dx.getFloat());
        dyModel.setValue(tf.dy.getFloat());
        sxModel.setValue(tf.scaleX.getFloat());
        syModel.setValue(tf.scaleY.getFloat());
        rotModel.setValue(tf.dRotation.getFloat());
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
                TransModifier tf = (TransModifier) modifier;
                UndoManager.TransModifierState newState = new UndoManager.TransModifierState(
                    tf.dx.getFloat(), tf.dy.getFloat(),
                    tf.scaleX.getFloat(), tf.scaleY.getFloat(),
                    tf.dRotation.getFloat(),
                    tf.flipX(), tf.flipY());
                if (!undoOldState.equals(newState)) {
                    p.undoManager.record(new UndoManager.TransformModifierCommand(
                        tf, undoOldState, newState));
                }
            }
            lastEditTime = -1;
            undoOldState = null;
        }
    }
}
