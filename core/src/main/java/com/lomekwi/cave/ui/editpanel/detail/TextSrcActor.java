package com.lomekwi.cave.ui.editpanel.detail;

import static com.lomekwi.cave.util.i18n.I18N.i18n;

import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTextField;
import com.lomekwi.cave.app.App;
import com.lomekwi.cave.pipeline.text.TextSrc;
import com.lomekwi.cave.timeline.playback.RefreshRequestEvent;

public class TextSrcActor extends SourceActor {

    public TextSrcActor(TextSrc src) {
        super(src.getDisplayName());
        VisLabel label = new VisLabel(i18n("文本: "));
        add(label).pad(4).left();
        VisTextField textField = new VisTextField(src.getText());
        add(textField).growX().pad(4).row();
        textField.setTextFieldListener((field, c) -> {
            src.setText(field.getText());
            var p = App.root.getFrontendProject();
            if (p != null) {
                p.projEventBus.post(RefreshRequestEvent.INSTANCE);
            }
        });
    }
}
