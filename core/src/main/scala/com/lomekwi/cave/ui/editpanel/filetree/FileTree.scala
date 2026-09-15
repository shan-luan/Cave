package com.lomekwi.cave.ui.editpanel.filetree


import com.badlogic.gdx.Gdx
import com.kotcrab.vis.ui.widget.VisTree

import java.io.File

import com.lomekwi.cave.util.i18n.I18N.i18n
import scala.compiletime.uninitialized

class FileTree private extends VisTree[FileTreeNode, File]() {
  {
    val rootFile = new File(System.getProperty("user.home"))
    Gdx.app.debug("FileTree", i18n("创建文件树，根目录: ") + rootFile.getAbsolutePath)
    val rootNode = new FileTreeNode(rootFile)

    add(rootNode)
  }

  def addRootDirectory(directory: File): Unit = {
    val rootNode = new FileTreeNode(directory)
    add(rootNode)
    rootNode.setExpanded(true)
  }
}

object FileTree {
  private var INSTANCE: FileTree = uninitialized

  def getINSTANCE: FileTree = {
    if (INSTANCE == null) {
      INSTANCE = new FileTree()
    }
    INSTANCE
  }
}
