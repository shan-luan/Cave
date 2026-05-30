package com.lomekwi.cave.ui.tabs;

import com.google.common.eventbus.Subscribe;
import com.kotcrab.vis.ui.widget.tabbedpane.Tab;
import com.kotcrab.vis.ui.widget.tabbedpane.TabbedPane;
import com.kotcrab.vis.ui.widget.tabbedpane.TabbedPaneListener;
import com.lomekwi.cave.project.ProjectBackgroundedEvent;
import com.lomekwi.cave.project.ProjectFrontedEvent;
import com.lomekwi.cave.project.ProjectLoadedEvent;
import com.lomekwi.cave.ui.Root;
import com.lomekwi.cave.app.App;

public class TopTabbedPane extends TabbedPane {
    private ProjectTab currentProjectTab;

    public TopTabbedPane() {
        super();
        App.appEventBus.register(this);
        addListener(new TabbedPaneListener() {
            @Override
            public void switchedTab(Tab tab) {
                Root.getInstance().getMajorArea().clear();
                Root.getInstance().getMajorArea().add(tab.getContentTable()).grow();

                if (currentProjectTab != null && currentProjectTab != tab) {
                    currentProjectTab.getProject().projEventBus.post(ProjectBackgroundedEvent.INSTANCE);
                }

                if (tab instanceof ProjectTab) {
                    currentProjectTab = (ProjectTab) tab;
                    ((ProjectTab) tab).getProject().projEventBus.post(ProjectFrontedEvent.INSTANCE);
                } else {
                    currentProjectTab = null;
                }

                App.appEventBus.post(TabSwitchedEvent.INSTANCE);
            }

            @Override
            public void removedTab(Tab tab) {
                if (tab instanceof ProjectTab) {
                    ((ProjectTab) tab).getProject().projEventBus.post(ProjectBackgroundedEvent.INSTANCE);
                    if (currentProjectTab == tab) {
                        currentProjectTab = null;
                    }
                }
            }

            @Override
            public void removedAllTabs() {
                Root.getInstance().getMajorArea().clear();
                App.appEventBus.post(TabSwitchedEvent.INSTANCE);
            }
        });
    }

    public void add(Tab... tabs){
        for(Tab tab : tabs){
            super.add(tab);
        }
    }
    @Subscribe
    public void onNewProject(ProjectLoadedEvent event){
        ProjectTab pt = new ProjectTab(event.getNewProject());
        add(pt);
        switchTab(pt);
    }
    @Subscribe
    public void onSettingsOpened(SettingsOpenedEvent event){
        SettingsTab st = new SettingsTab();
        add(st);
        switchTab(st);
    }
}
