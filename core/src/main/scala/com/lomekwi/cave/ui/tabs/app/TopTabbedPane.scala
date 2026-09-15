package com.lomekwi.cave.ui.tabs.app

import com.google.common.eventbus.Subscribe
import com.kotcrab.vis.ui.widget.tabbedpane.Tab
import com.kotcrab.vis.ui.widget.tabbedpane.TabbedPaneListener
import com.lomekwi.cave.project.ProjectBackgroundedEvent
import com.lomekwi.cave.project.ProjectFrontedEvent
import com.lomekwi.cave.project.ProjectLoadedEvent
import com.lomekwi.cave.app.App
import com.lomekwi.cave.ui.widget.AutoHideTabbedPane
import scala.compiletime.uninitialized

class TopTabbedPane extends AutoHideTabbedPane {
  private var currentProjectTab: ProjectTab = uninitialized

  {
    App.appEventBus.register(this)
    addListener(new TabbedPaneListener {
      override def switchedTab(tab: Tab): Unit = {
        if (currentProjectTab != null && (currentProjectTab ne tab)) {
          currentProjectTab.getProject.playhead.setPlaying(false)
        }

        App.root.getMajorArea.setActor(tab.getContentTable)

        if (currentProjectTab != null && (currentProjectTab ne tab)) {
          currentProjectTab.getProject.projEventBus.post(ProjectBackgroundedEvent)
        }

        tab match {
          case pt: ProjectTab =>
            currentProjectTab = pt
            pt.getProject.projEventBus.post(ProjectFrontedEvent)
          case _ =>
            currentProjectTab = null
        }

        App.appEventBus.post(TabSwitchedEvent)
      }

      override def removedTab(tab: Tab): Unit = {
        tab match {
          case pt: ProjectTab =>
            pt.getProject.playhead.setPlaying(false)
            pt.getProject.projEventBus.post(ProjectBackgroundedEvent)
            if (currentProjectTab eq tab) {
              currentProjectTab = null
            }
          case _ =>
        }
      }

      override def removedAllTabs(): Unit = {
        App.root.getMajorArea.setActor(null)
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
