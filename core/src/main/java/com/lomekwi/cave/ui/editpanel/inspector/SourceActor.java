package com.lomekwi.cave.ui.editpanel.inspector;

import com.badlogic.gdx.utils.Align;
import com.kotcrab.vis.ui.widget.VisTable;
import com.lomekwi.cave.pipeline.Source;
import com.lomekwi.cave.ui.widget.Card;

/** 通用源信息卡：由 {@code source.getParams()} 自动生成内容，无子类。 */
public final class SourceActor extends Card {
    public SourceActor(Source<?> source) {
        super(source.getDisplayName());
        align(Align.top | Align.left);
        defaults().left();
        new ParamBinder(() -> source).bind(this, source.getParams());
    }
}
