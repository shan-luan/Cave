package com.lomekwi.cave.ui.editpanel.settings;

import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTree;

import java.util.function.Supplier;

public class EntryNode extends VisTree.Node<EntryNode, EntryTable, VisLabel> {
    private final Supplier<EntryTable> supplier;

    public EntryNode(String name, Supplier<EntryTable> supplier) {
        super(new VisLabel(name));
        this.supplier = supplier;
    }

    @Override
    public EntryTable getValue() {
        return supplier.get();
    }
}
