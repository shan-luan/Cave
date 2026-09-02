package com.lomekwi.cave.ui.widget;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisTextArea;
import com.lomekwi.cave.app.App;
import com.lomekwi.cave.pipeline.Node;
import com.lomekwi.cave.pipeline.Source;
import com.lomekwi.cave.project.Project;
import com.lomekwi.cave.timeline.playback.RefreshRequestEvent;

import java.util.Objects;

/**
 * String 输入端口编辑 widget：VisTextArea 行。直接持有端口模型，修改写默认值并刷新预览。
 */
public final class TextPortEditor extends VisTable {
    public TextPortEditor(Node.InPort<?> port, Source<?> source) {
        String defaultValue = (String) port.getDefaultData();
        VisTextArea textArea = new VisTextArea(defaultValue == null ? "" : defaultValue);
        // 撑高 prefHeight，否则 X2 皮肤下 linesShowing 为 0，文字不会绘制
        textArea.setPrefRows(3);
        textArea.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                String newVal = textArea.getText();
                String oldVal = (String) port.getDefaultData();
                if (Objects.equals(oldVal, newVal)) return;
                @SuppressWarnings("unchecked")
                Node.InPort<String> strPort = (Node.InPort<String>) port;
                strPort.setDefaultData(newVal);
                Project p = App.root.getFrontendProject();
                if (p != null) {
                    p.projEventBus.post(RefreshRequestEvent.INSTANCE);
                }
            }
        });
        align(Align.topLeft);
        defaults().left();
        add(new VisLabel(port.getName())).pad(2).left().row();
        add(textArea).growX().pad(2);
    }
}