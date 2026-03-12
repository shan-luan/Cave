package com.lomekwi.cave.ui.tabs;

import com.google.common.eventbus.Subscribe;
import com.kotcrab.vis.ui.widget.tabbedpane.Tab;
import com.kotcrab.vis.ui.widget.tabbedpane.TabbedPane;
import com.kotcrab.vis.ui.widget.tabbedpane.TabbedPaneListener;
import com.lomekwi.cave.project.ProjectEvents;
import com.lomekwi.cave.ui.Root;
import com.lomekwi.cave.util.Vars;

public class TopTabbedPane extends TabbedPane {
    public TopTabbedPane() {
        super();
        Vars.appEventBus.register(this);
        addListener(new TabbedPaneListener() {
            @Override
            public void switchedTab(Tab tab) {
                Root.getInstance().getMajorArea().clear();
                Root.getInstance().getMajorArea().add(tab.getContentTable()).expand().fill();
            }

            @Override
            public void removedTab(Tab tab) {}

            @Override
            public void removedAllTabs() {}
        });
    }

    public void add(Tab... tabs){
        for(Tab tab : tabs){
            super.add(tab);
        }
    }
    @Subscribe
    public void onNewProject(ProjectEvents.ProjectLoadedEvent event){
        ProjectTab pt = new ProjectTab(event.getNewProject());
        add(pt);
        switchTab(pt);
    }
}
