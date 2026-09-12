package com.lomekwi.cave.ui.tabs.edit;

import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.kotcrab.vis.ui.widget.tabbedpane.Tab;
import com.lomekwi.cave.pipeline.NodeGraph;
import com.lomekwi.cave.ui.node.NodeEditorView;
import com.lomekwi.cave.util.i18n.I18N;

//TODO:WIP
public class NodeEditorTab extends Tab {
    private final NodeGraph nodeGraph;
    private final Table content;

    public NodeEditorTab(NodeGraph nodeGraph) {
        super(false, true);
        this.nodeGraph = nodeGraph;
        content = new NodeEditorView(nodeGraph);
    }

    public NodeGraph getNodeGraph() {
        return nodeGraph;
    }

    @Override
    public String getTabTitle() {
        return I18N.i18n("节点编辑器");
    }

    @Override
    public Table getContentTable() {
        return content;
    }
}
