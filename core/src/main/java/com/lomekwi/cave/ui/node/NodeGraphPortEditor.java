package com.lomekwi.cave.ui.node;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisTextButton;
import com.lomekwi.cave.app.App;
import com.lomekwi.cave.pipeline.Node;
import com.lomekwi.cave.pipeline.NodeGraph;
import com.lomekwi.cave.pipeline.Source;
import com.lomekwi.cave.ui.editpanel.EditPanel;

//TODO:WIP
/**
 * NodeGraph 输入端口编辑 widget：按钮，按下后在项目的内部标签栏打开绑定到该端口当前默认值节点图的编辑器。
 * 外层表格占满卡片宽度，按钮保持自身尺寸左对齐，避免被卡片的 growX 拉伸。
 */
public final class NodeGraphPortEditor extends VisTable implements PortEditor {
    private final Node.InPort<?> port;

    public NodeGraphPortEditor(Node.InPort<?> port, Source<?> source) {
        this.port = port;
        VisTextButton button = new VisTextButton(port.getName());
        button.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                openEditor();
            }
        });
        align(Align.left);
        defaults().left();
        add(button);
    }

    private void openEditor() {
        NodeGraph nodeGraph = (NodeGraph) port.getDefaultData();
        if (nodeGraph == null) return;
        EditPanel panel = App.root.getFrontendEditPanel();
        if (panel == null) return;
        panel.getTlTabs().openNodeEditor(nodeGraph);
    }

    @Override
    public Node.InPort<?> getPort() {
        return port;
    }
}
