package com.lomekwi.cave.ui.tabs.app

import com.google.common.eventbus.Subscribe
import com.kotcrab.vis.ui.widget.tabbedpane.Tab
import com.kotcrab.vis.ui.widget.tabbedpane.TabbedPaneListener
import com.lomekwi.cave.project.ProjectBackgroundedEvent
import com.lomekwi.cave.project.ProjectFrontedEvent
import com.lomekwi.cave.project.ProjectLoadedEvent
import com.lomekwi.cave.app.App
import com.lomekwi.cave.ui.widget.AutoHideTabbedPane

class TopTabbedPane extends AutoHideTabbedPane {
  private var currentProjectTab: ProjectTab = null

  {
    App.appEventBus.register(this)
    addListener(new TabbedPaneListener {
      override def switchedTab(tab: Tab): Unit = {
        if (currentProjectTab != null && (currentProjectTab ne tab)) {
          currentProjectTab.getProject().playhead.setPlaying(false)
        }

        App.root.getMajorArea().setActor(tab.getContentTable)

        if (currentProjectTab != null && (currentProjectTab ne tab)) {
          currentProjectTab.getProject().projEventBus.post(ProjectBackgroundedEvent)
        }

        if (tab.isInstanceOf[ProjectTab]) {
          currentProjectTab = tab.asInstanceOf[ProjectTab]
          tab.asInstanceOf[ProjectTab].getProject().projEventBus.post(ProjectFrontedEvent)
        } else {
          currentProjectTab = null
        }

        App.appEventBus.post(TabSwitchedEvent)
      }

      override def removedTab(tab: Tab): Unit = {
        if (tab.isInstanceOf[ProjectTab]) {
          tab.asInstanceOf[ProjectTab].getProject().playhead.setPlaying(false)
          tab.asInstanceOf[ProjectTab].getProject().projEventBus.post(ProjectBackgroundedEvent)
          if (currentProjectTab eq tab) {
            currentProjectTab = null
          }
        }
      }

      override def removedAllTabs(): Unit = {
        App.root.getMajorArea().setActor(null)
        App.appEventBus.post(TabSwitchedEvent)
      }
    })
  }

  def add(tabs: Tab*): Unit = {
    for (tab <- tabs) {
      super.add(tab)
    }
  }
  @Subscribe
  def onNewProject(event: ProjectLoadedEvent): Unit = {
    val pt = new ProjectTab(event.newProject)
    super.add(pt)
    switchTab(pt)
  }
}
