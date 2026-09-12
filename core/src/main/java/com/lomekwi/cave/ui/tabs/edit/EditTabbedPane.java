package com.lomekwi.cave.ui.tabs.edit;

import com.badlogic.gdx.scenes.scene2d.ui.Container;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.kotcrab.vis.ui.widget.tabbedpane.Tab;
import com.kotcrab.vis.ui.widget.tabbedpane.TabbedPaneListener;
import com.lomekwi.cave.pipeline.NodeGraph;
import com.lomekwi.cave.ui.widget.AutoHideTabbedPane;

public class EditTabbedPane extends AutoHideTabbedPane {
    private final Container<Table> contentHost = new Container<>() {
        @Override
        public float getMinHeight() {
            return 0;
        }
        @Override
        public float getMinWidth() {
            return 0;
        }
        @Override
        public float getPrefHeight() {
            return 0;
        }
        @Override
        public float getPrefWidth() {
            return 0;
        }
    };

    public EditTabbedPane() {
        super();
        contentHost.fill();
        addListener(new TabbedPaneListener() {
            @Override
            public void switchedTab(Tab tab) {
                contentHost.setActor(tab == null ? null : tab.getContentTable());
            }

            @Override
            public void removedTab(Tab tab) {
                if (getTabs().size == 0) contentHost.setActor(null);
            }

            @Override
            public void removedAllTabs() {
                contentHost.setActor(null);
            }
        });
    }

    public Container<Table> getContentHost() {
        return contentHost;
    }

    //TODO:WIP
    /** 打开绑定到指定节点图的编辑器标签页；已为同一个节点图打开过则直接切换过去。 */
    public void openNodeEditor(NodeGraph nodeGraph) {
        for (Tab tab : getTabs()) {
            if (tab instanceof NodeEditorTab editor && editor.getNodeGraph() == nodeGraph) {
                switchTab(editor);
                return;
            }
        }
        NodeEditorTab editor = new NodeEditorTab(nodeGraph);
        add(editor);
        switchTab(editor);
    }
}
