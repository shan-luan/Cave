package com.lomekwi.cave.ui.node;

import com.lomekwi.cave.pipeline.Node;
import com.lomekwi.cave.ui.widget.Card;

//TODO:WIP
public class NodeActor extends Card {
    private final Node node;
    public NodeActor(Node node) {
        super(node.getName());
        this.node=node;
    }
}
