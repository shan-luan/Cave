package com.lomekwi.cave.ui.editpanel.inspector;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.utils.Align;
import com.lomekwi.cave.pipeline.Node;
import com.lomekwi.cave.pipeline.Source;
import com.lomekwi.cave.ui.widget.Card;

/**
 * 通用源信息卡：显示源名称、输入端口与信息输出端口（不含参与 filter 链的 FilterOut）。
 * 类型 → widget 的映射由 {@link CardWidgetsRegistry} 维护。
 */
public final class SourceActor extends Card {
    private final Source<?> source;

    public SourceActor(Source<?> source) {
        super(source.getDisplayName());
        this.source = source;
        align(Align.top | Align.left);
        defaults().left();
        for (Node.InPort<?> in : source.getInPorts()) {
            Actor widget = CardWidgetsRegistry.createEditor(in, source);
            if (widget == null) continue; // 未注册该端口类型的 widget，不显示
            add(widget).growX().pad(2).row();
        }
        for (Node.OutPort<?> out : source.getOutPorts()) {
            Actor row = CardWidgetsRegistry.createOutputRow(out);
            if (row == null) continue; // FilterOut 与未知类型的输出端口不显示
            add(row).pad(2).left().row();
        }
    }
}
