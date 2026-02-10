package com.lomekwi.cine.ui.tabs;

import com.badlogic.gdx.Gdx;
import com.kotcrab.vis.ui.widget.tabbedpane.Tab;
import com.kotcrab.vis.ui.widget.tabbedpane.TabbedPane;
import com.kotcrab.vis.ui.widget.tabbedpane.TabbedPaneListener;
import com.lomekwi.cine.ui.Root;

public class TopTabbedPane extends TabbedPane {
    public TopTabbedPane() {
        super();
        addListener(new TabbedPaneListener() {
            @Override
            public void switchedTab(Tab tab) {
                Root.getInstance().getMajorArea().clear();
                Root.getInstance().getMajorArea().add(tab.getContentTable());
            }

            @Override
            public void removedTab(Tab tab) {}

            @Override
            public void removedAllTabs() {
                Gdx.app.postRunnable(()->add(new ProjectTab()));
            }
        });
    }

    public void add(Tab... tabs){
        for(Tab tab : tabs){
            super.add(tab);
        }
    }

}
