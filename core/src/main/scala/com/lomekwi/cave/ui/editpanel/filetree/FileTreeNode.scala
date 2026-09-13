package com.lomekwi.cave.ui.editpanel.filetree

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.ui.Tree
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop
import com.badlogic.gdx.scenes.scene2d.utils.Layout
import com.kotcrab.vis.ui.widget.VisLabel

import com.lomekwi.cave.app.App
import com.lomekwi.cave.util.MimeType

import java.io.File

import com.lomekwi.cave.util.i18n.I18N.i18n

class FileTreeNode(file: File) extends Tree.Node[FileTreeNode, File, VisLabel]() {
  private var childrenLoaded: Boolean = false

  setActor(new DraggableLabel(if (file != null) file.getName() else ""))
  setValue(file)

  if (file != null && file.isDirectory()) {
    Gdx.app.debug("FileTreeNode", i18n("创建目录节点: ") + file.getName())
    add(FileTreeNode.PLACEHOLDER_NODE)
  }

  if (file != null && file.isFile()) {
    val mimeType = MimeType.detectMimeType(file)
    if (!App.mediaFactory.isSupported(mimeType)) {
      getActor().setColor(Color.GRAY)
    }
  }

  override def setExpanded(expanded: Boolean): Unit = {
    super.setExpanded(expanded)

    if (expanded && !childrenLoaded) {
      Gdx.app.debug("FileTreeNode", i18n("展开目录，开始加载子节点: ") + getValue().getName())
      loadChildren()
      childrenLoaded = true
      Gdx.app.debug("FileTreeNode", i18n("子节点加载完成: ") + getValue().getName())
    }
  }

  private def loadChildren(): Unit = {
    val file = getValue()
    if (file != null && file.isDirectory()) {
      getChildren().removeValue(FileTreeNode.PLACEHOLDER_NODE, true)

      val children = file.listFiles()
      if (children != null) {
        Gdx.app.debug("FileTreeNode", i18n("找到 ") + children.length + i18n(" 个子项"))
        for (child <- children) {
          if (!child.getName().startsWith(".")) {
            val childNode = new FileTreeNode(child)
            add(childNode)
          }
        }
      }
    }
  }
  class DraggableLabel(text: String) extends VisLabel(text) {
    private var dragActor: VisLabel = null
    App.root.getDragAndDrop().addSource(new DragAndDrop.Source(this) {
      override def dragStart(event: InputEvent, x: Float, y: Float, pointer: Int): DragAndDrop.Payload = {
        val payload = new DragAndDrop.Payload()
        payload.setObject(getValue())

        dragActor = new VisLabel(getText().toString())
        payload.setDragActor(dragActor)
        payload
      }
      override def drag(event: InputEvent, x: Float, y: Float, pointer: Int): Unit = {
        super.drag(event, x, y, pointer)
      }

      override def dragStop(event: InputEvent, x: Float, y: Float, pointer: Int, payload: DragAndDrop.Payload, target: DragAndDrop.Target): Unit = {
        dragActor = null
      }
    })
  }
}

object FileTreeNode {
  private final val PLACEHOLDER_NODE: FileTreeNode = new FileTreeNode(null)
}
