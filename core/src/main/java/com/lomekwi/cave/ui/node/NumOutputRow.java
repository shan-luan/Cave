package com.lomekwi.cave.ui.node;

import com.kotcrab.vis.ui.widget.VisLabel;
import com.lomekwi.cave.pipeline.Node;
import com.lomekwi.cave.pipeline.num.NumFrame;

/**
 * NumFrame 输出端口显示：只读数值行。
 */
public final class NumOutputRow extends VisLabel implements PortRow {
    private final Node.OutPort<?> port;

    public NumOutputRow(Node.OutPort<?> port) {
        super(port.getName() + ": " + ((NumFrame) port.getData()).getVal());
        this.port = port;
    }

    @Override
    public Node.OutPort<?> getPort() {
        return port;
    }
}
