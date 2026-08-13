package com.lomekwi.cave.ui.tabs;

import com.badlogic.gdx.scenes.scene2d.ui.Container;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.kotcrab.vis.ui.widget.tabbedpane.Tab;
import com.kotcrab.vis.ui.widget.tabbedpane.TabbedPaneListener;

public class TlTabbedPane extends AutoHideTabbedPane {
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

    public TlTabbedPane() {
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
}
