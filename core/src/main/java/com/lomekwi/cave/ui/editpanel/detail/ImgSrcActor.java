package com.lomekwi.cave.ui.editpanel.detail;

import static com.lomekwi.cave.util.i18n.I18N.i18n;

import com.kotcrab.vis.ui.widget.VisLabel;
import com.lomekwi.cave.pipeline.image.ImgSrc;

public class ImgSrcActor extends SourceActor {
    public ImgSrcActor(ImgSrc src) {
        super(src.getDisplayName());
        add(new VisLabel(i18n("路径: ") + src.getImgRes().getPath())).pad(4).row();
        add(new VisLabel(i18n("分辨率: ") + src.getImgRes().getWidth() + " × " + src.getImgRes().getHeight())).pad(4).row();
    }
}
