package com.lomekwi.cave.ui.editpanel.detail;

import static com.lomekwi.cave.util.i18n.I18N.i18n;

import com.kotcrab.vis.ui.widget.VisLabel;
import com.lomekwi.cave.pipeline.image.ImgSrc;

public class ImgSrcActor extends SourceActor {
    public ImgSrcActor(ImgSrc src) {
        super(src.getDisplayName());
        add(new VisLabel(i18n("\u8def\u5f84: ") + src.getImgRes().getPath())).pad(4).row();
        add(new VisLabel(i18n("\u5206\u8fa8\u7387: ") + src.getImgRes().getWidth() + " \u00d7 " + src.getImgRes().getHeight())).pad(4).row();
        add(new VisLabel(i18n("\u603b\u65f6\u957f: ") + src.getDuration() / 1_000_000.0 + "s")).pad(4);
    }
}
