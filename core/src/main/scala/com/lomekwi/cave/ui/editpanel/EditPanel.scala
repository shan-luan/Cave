package com.lomekwi.cave.ui.editpanel

import com.badlogic.gdx.scenes.scene2d.ui.Container
import com.lomekwi.cave.project.Project
import com.lomekwi.cave.ui.editpanel.previewarea.PreviewArea
import com.lomekwi.cave.ui.editpanel.mediapool.MediaPool
import com.lomekwi.cave.ui.editpanel.tlarea.TlGroup
import com.lomekwi.cave.ui.tabs.edit.TimelineTab
import com.lomekwi.cave.ui.tabs.edit.EditTabbedPane
import com.kotcrab.vis.ui.widget.VisTable

class EditPanel(private[editpanel] val project: Project) {
  private[editpanel] var previewArea: PreviewArea = null
  private[editpanel] var tl: VisTable = null
  private[editpanel] var tlMain: Container[TlGroup] = null
  private[editpanel] var res: Container[MediaPool] = null
  private[editpanel] var tlTabs: EditTabbedPane = null

  {
    previewArea = new PreviewArea(project)
    tlMain = new Container[TlGroup](new TlGroup(project)).fill().clip().minSize(0, 0)
    tlTabs = new EditTabbedPane()
    tlTabs.add(new TimelineTab(tlMain))
    tl = new VisTable()
    tl.add(tlTabs.getTable()).fillX().top().row()
    tl.add(tlTabs.getContentHost()).grow()
    tlTabs.refreshVisibility()
    res = new Container[MediaPool](new MediaPool(project.resources, project.projEventBus))
  }

  def getPreviewArea(): PreviewArea = {
    previewArea
  }

  def getTlGroup(): TlGroup = {
    tlMain.getActor()
  }

  def getTlTabs(): EditTabbedPane = {
    tlTabs
  }

  def dispose(): Unit = {
    previewArea.dispose()
    tlMain.getActor().dispose()
  }
}
