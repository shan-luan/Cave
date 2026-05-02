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
                if (tab instanceof ProjectTab) {
                    ((ProjectTab) tab).getProject().projEventBus.post(ProjectEvents.ProjectFrontedEvent.INSTANCE);
                }
                Vars.appEventBus.post(TabEvents.TabSwitchedEvent.INSTANCE);
            }

            @Override
            public void removedTab(Tab tab) {
            }

            @Override
            public void removedAllTabs() {
                Root.getInstance().getMajorArea().clear();
                Vars.appEventBus.post(TabEvents.TabSwitchedEvent.INSTANCE);
            }
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
    @Subscribe
    public void onSettingsOpened(TabEvents.SettingsOpenedEvent event){
        SettingsTab st = new SettingsTab();
        add(st);
        switchTab(st);
    }
}
