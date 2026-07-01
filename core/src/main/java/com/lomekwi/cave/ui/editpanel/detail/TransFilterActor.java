package com.lomekwi.cave.ui.editpanel.detail;

import static com.lomekwi.cave.util.i18n.I18N.i18n;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.spinner.SimpleFloatSpinnerModel;
import com.kotcrab.vis.ui.widget.spinner.Spinner;
import com.lomekwi.cave.app.App;
import com.lomekwi.cave.pipeline.image.TransFilter;
import com.lomekwi.cave.project.Project;
import com.lomekwi.cave.timeline.playback.RefreshRequestEvent;

public class TransFilterActor extends FilterActor {
    public TransFilterActor(TransFilter filter) {
        super(filter.getDisplayName());

        var dxModel = new SimpleFloatSpinnerModel(filter.dx(), -9999, 9999, 1, 1);
        var dyModel = new SimpleFloatSpinnerModel(filter.dy(), -9999, 9999, 1, 1);
        var sxModel = new SimpleFloatSpinnerModel(filter.scaleX(), 0.01f, 100, 0.01f, 2);
        var syModel = new SimpleFloatSpinnerModel(filter.scaleY(), 0.01f, 100, 0.01f, 2);
        var rotModel = new SimpleFloatSpinnerModel(filter.dRotation(), -9999, 9999, 1, 1);

        var dxSpinner = new Spinner("", dxModel);
        var dySpinner = new Spinner("", dyModel);
        var sxSpinner = new Spinner("", sxModel);
        var sySpinner = new Spinner("", syModel);
        var rotSpinner = new Spinner("", rotModel);

        ChangeListener updater = new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                Spinner s = (Spinner) actor;
                SimpleFloatSpinnerModel m = (SimpleFloatSpinnerModel) s.getModel();
                float v = m.getValue();
                if (s == dxSpinner) filter.dx(v);
                else if (s == dySpinner) filter.dy(v);
                else if (s == sxSpinner) filter.scaleX(v);
                else if (s == sySpinner) filter.scaleY(v);
                else if (s == rotSpinner) filter.dRotation(v);
                Project p = App.root.getFrontendProject();
                if (p != null) p.projEventBus.post(RefreshRequestEvent.INSTANCE);
            }
        };
        dxSpinner.addListener(updater);
        dySpinner.addListener(updater);
        sxSpinner.addListener(updater);
        sySpinner.addListener(updater);
        rotSpinner.addListener(updater);

        add(new VisLabel(i18n("位移 X"))).pad(4);
        add(dxSpinner).width(90).pad(4).row();
        add(new VisLabel(i18n("位移 Y"))).pad(4);
        add(dySpinner).width(90).pad(4).row();
        add(new VisLabel(i18n("缩放 X"))).pad(4);
        add(sxSpinner).width(90).pad(4).row();
        add(new VisLabel(i18n("缩放 Y"))).pad(4);
        add(sySpinner).width(90).pad(4).row();
        add(new VisLabel(i18n("旋转"))).pad(4);
        add(rotSpinner).width(90).pad(4);
    }
}
