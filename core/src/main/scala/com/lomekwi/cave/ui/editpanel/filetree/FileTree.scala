package com.lomekwi.cave.ui.editpanel.filetree


import com.badlogic.gdx.Gdx
import com.kotcrab.vis.ui.widget.VisTree

import java.io.File

import com.lomekwi.cave.util.i18n.*
import com.lomekwi.cave.util.i18n.BasicLang.*
import com.lomekwi.cave.util.i18n.TransTargetType.given

class FileTree private extends VisTree[FileTreeNode, File]() {
  {
    val rootFile = new File(System.getProperty("user.home"))
    Gdx.app.debug("FileTree", ("创建文件树，根目录: ".in(Zh) | "Creating file tree, root: ".in(En)).str() + rootFile.getAbsolutePath)
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
  private lazy val INSTANCE: FileTree = new FileTree()

  def getINSTANCE: FileTree = INSTANCE
}
