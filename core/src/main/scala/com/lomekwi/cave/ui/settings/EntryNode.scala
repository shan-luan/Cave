package com.lomekwi.cave.ui.settings

import com.badlogic.gdx.scenes.scene2d.ui.Tree
import com.kotcrab.vis.ui.widget.VisLabel

import java.util.function.Supplier

class EntryNode(name: String, private val supplier: Supplier[EntryTable]) extends Tree.Node[EntryNode, EntryTable, VisLabel](new VisLabel(name)) {
  override def getValue(): EntryTable = {
    supplier.get()
  }
}
