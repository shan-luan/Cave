package com.lomekwi.cave.ui.editpanel.settings;

import static com.lomekwi.cave.util.i18n.I18N.i18n;

import com.kotcrab.vis.ui.widget.VisDialog;
import com.lomekwi.cave.app.App;

public class SettingsDialog extends VisDialog {

    public SettingsDialog() {
        super(i18n("设置"));

        addCloseButton();

        getContentTable().add(new SettingsTable()).grow();

        show(App.root.getStage());
        setSize(800, 600);
        centerWindow();
    }
}
