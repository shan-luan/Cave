package com.lomekwi.cave.ui.editpanel.inspector;

import com.lomekwi.cave.pipeline.Node;
import com.lomekwi.cave.pipeline.num.NumFrame;

/**
 * 端口 → widget 的绑定决策：用 {@link Node.InPort#getConstraint()} 与
 * {@link Node.OutPort#getType()} 判断端口是否与数值帧兼容，而不是靠端口的具体实现类（instanceof）。
 */
final class PortWidgets {
    private PortWidgets() {
    }

    /** 输入端口的约束允许连接 {@link NumFrame}，即可绑定数值编辑 widget。 */
    static boolean acceptsNumFrame(Node.InPort<?> port) {
        return accepts(port, NumFrame.class);
    }

    /** 输入端口的约束允许连接 {@link String}，即可绑定文本输入 widget。 */
    static boolean acceptsString(Node.InPort<?> port) {
        return accepts(port, String.class);
    }

    private static boolean accepts(Node.InPort<?> port, Class<?> type) {
        for (Class<?> c : port.getConstraint()) {
            if (!c.isAssignableFrom(type)) {
                return false;
            }
        }
        return true;
    }

    /** 输出端口声明的类型为 {@link NumFrame} 或其子类，即可显示数值。 */
    static boolean outputsNumFrame(Node.OutPort<?> port) {
        Class<?> type = port.getType();
        return type != null && NumFrame.class.isAssignableFrom(type);
    }
}
