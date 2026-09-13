package com.lomekwi.cave.ui.editpanel

import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.kotcrab.vis.ui.widget.VisSplitPane
import com.kotcrab.vis.ui.widget.VisTable
import com.kotcrab.vis.ui.widget.VisScrollPane
import com.kotcrab.vis.ui.widget.VisTextButton
import com.kotcrab.vis.ui.widget.file.FileChooser
import com.kotcrab.vis.ui.widget.file.SingleFileChooserListener
import com.lomekwi.cave.app.App
import com.lomekwi.cave.ui.editpanel.inspector.Inspector
import com.lomekwi.cave.ui.editpanel.filetree.FileTree

class EditPanelFrame private () extends VisTable {
  private var mediaPoolAndFileTreeSplitPane: VisSplitPane = null
  private var previewAndTimelineSplitPane: VisSplitPane = null
  private var previewAndDetailSplitPane: VisSplitPane = null
  private var detailPanel: Inspector = null
  private var editPanel: EditPanel = null

  {
    val treePanel = new VisTable()
    treePanel.add(FileTree.getINSTANCE()).grow().row()
    val addDirBtn = new VisTextButton("+")
    addDirBtn.addListener(new ChangeListener {
      override def changed(event: ChangeListener.ChangeEvent, actor: Actor): Unit = {
        val chooser = new FileChooser("选择要添加到文件树的目录", FileChooser.Mode.OPEN)
        chooser.setSelectionMode(FileChooser.SelectionMode.DIRECTORIES)
        chooser.setListener(new SingleFileChooserListener {
          override protected def selected(file: FileHandle): Unit = {
            FileTree.getINSTANCE().addRootDirectory(file.file())
          }
        })
        App.root.getStage().addActor(chooser)
      }
    })
    treePanel.add(addDirBtn).fillX()
    val fileTreeScrollPane: ScrollPane = new VisScrollPane(treePanel)
    mediaPoolAndFileTreeSplitPane = new VisSplitPane(null, fileTreeScrollPane, true)
    mediaPoolAndFileTreeSplitPane.setSplitAmount(0.33f)
    detailPanel = new Inspector()
    previewAndDetailSplitPane = new VisSplitPane(null, detailPanel, false)
    previewAndDetailSplitPane.setSplitAmount(0.75f)
    previewAndTimelineSplitPane = new VisSplitPane(previewAndDetailSplitPane, null, true)
    previewAndTimelineSplitPane.setSplitAmount(0.615f)
    val mainSplitPane = new VisSplitPane(mediaPoolAndFileTreeSplitPane, previewAndTimelineSplitPane, false)
    mainSplitPane.setSplitAmount(0.18f)
    add(mainSplitPane).grow()
  }

  def `with`(editPanel: EditPanel): EditPanelFrame = {
    if (!editPanel.equals(this.editPanel)) {
      this.editPanel = editPanel
      mediaPoolAndFileTreeSplitPane.setFirstWidget(new VisScrollPane(editPanel.res.fill()))
      previewAndDetailSplitPane.setFirstWidget(editPanel.previewArea)
      previewAndTimelineSplitPane.setSecondWidget(editPanel.tl)
      editPanel.project.projEventBus.register(detailPanel)
    }
    this
  }

  def getDetailPanel(): Inspector = {
    detailPanel
  }

  override def getMinHeight(): Float = {
    0
  }
  override def getMinWidth(): Float = {
    0
  }
  override def getPrefHeight(): Float = {
    0
  }
  override def getPrefWidth(): Float = {
    0
  }
}

object EditPanelFrame {
  private var INSTANCE: EditPanelFrame = null

  def getINSTANCE(): EditPanelFrame = {
    if (INSTANCE == null) {
      INSTANCE = new EditPanelFrame()
    }
    INSTANCE
  }
}
