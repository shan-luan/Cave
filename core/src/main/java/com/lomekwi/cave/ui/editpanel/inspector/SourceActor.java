package com.lomekwi.cave.ui.editpanel.inspector;

import com.badlogic.gdx.utils.Align;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.lomekwi.cave.pipeline.Filter;
import com.lomekwi.cave.pipeline.Node;
import com.lomekwi.cave.pipeline.NumOutPort;
import com.lomekwi.cave.pipeline.Source;
import com.lomekwi.cave.ui.widget.Card;

/**
 * 通用源信息卡：显示源名称、输入端口与信息输出端口（不含参与 filter 链的 FilterOut）。
 */
public final class SourceActor extends Card {
    public SourceActor(Source<?> source) {
        super(source.getDisplayName());
        align(Align.top | Align.left);
        defaults().left();
        for (Node.InPort<?> in : source.getInPorts()) {
            add(new VisLabel("> " + in.getName())).pad(2).left().row();
        }
        for (Node.OutPort<?> out : source.getOutPorts()) {
            if (out instanceof Filter.FilterOut) continue; // 链头输出不显示
            if (out instanceof NumOutPort numOut) {
                String value = String.valueOf(numOut.getData().getVal());
                add(new VisLabel("< " + out.getName() + ": " + value)).pad(2).left().row();
            }
        }
    }
}