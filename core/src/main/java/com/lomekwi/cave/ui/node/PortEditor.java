package com.lomekwi.cave.ui.node;

import com.lomekwi.cave.pipeline.Node;

public interface PortEditor extends PortHolder {
    @Override
    Node.InPort<?> getPort();
}
