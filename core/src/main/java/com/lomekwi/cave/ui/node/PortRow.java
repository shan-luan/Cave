package com.lomekwi.cave.ui.node;

import com.lomekwi.cave.pipeline.Node;

public interface PortRow extends PortHolder {
    @Override
    Node.OutPort<?> getPort();
}
