package com.lomekwi.cave.ui.editpanel;

import static com.lomekwi.cave.util.i18n.I18N.i18n;

import com.kotcrab.vis.ui.widget.VisLabel;
import com.lomekwi.cave.pipeline.image.VdoClipSrc;

public class VdoClipSrcActor extends SourceActor {
    public VdoClipSrcActor(VdoClipSrc src) {
        super(src.getDisplayName());
        add(new VisLabel(i18n("路径: ") + src.getVdoRes().getPath())).pad(4).row();
        add(new VisLabel(i18n("分辨率: ") + src.getVdoRes().getWidth() + " \u00d7 " + src.getVdoRes().getHeight())).pad(4).row();
        add(new VisLabel(i18n("总时长: ") + src.getDuration() / 1_000_000.0 + "s")).pad(4);
    }
}
