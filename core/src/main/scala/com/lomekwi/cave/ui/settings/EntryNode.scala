package com.lomekwi.cave.ui.settings

import com.badlogic.gdx.scenes.scene2d.ui.Tree
import com.kotcrab.vis.ui.widget.VisLabel

class EntryNode(name: String, private val supplier: () => EntryTable) extends Tree.Node[EntryNode, EntryTable, VisLabel](new VisLabel(name)) {
  override def getValue: EntryTable = {
    supplier()
  }
}
