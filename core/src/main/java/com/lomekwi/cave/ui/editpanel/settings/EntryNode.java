package com.lomekwi.cave.ui.editpanel.settings;

import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTree;

public class EntryNode extends VisTree.Node<EntryNode, EntryTable, VisLabel>{
    public EntryNode(EntryTable entryTable){
        super(new VisLabel(entryTable.getName()));
        setValue(entryTable);
    }
}
