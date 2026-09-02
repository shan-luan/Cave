package com.lomekwi.cave.ui.editpanel.inspector;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.lomekwi.cave.pipeline.Node;
import com.lomekwi.cave.pipeline.Source;
import com.lomekwi.cave.pipeline.num.NumFrame;
import com.lomekwi.cave.ui.widget.NumOutputRow;
import com.lomekwi.cave.ui.widget.NumPortEditor;
import com.lomekwi.cave.ui.widget.TextPortEditor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Inspector 卡片的 widget 注册表：只维护 类型 → widget 工厂 的映射与按类型匹配。
 * 每个输入 widget 本身就是 Actor（直接持有端口模型，如 {@link NumPortEditor}、
 * {@link TextPortEditor}），输出行亦然（{@link NumOutputRow}）。注册表本身与端口无关，
 * 其他视图类型也可以注册自己的条目；需要支持新的类型时在这里注册一行即可，
 * 无需改动 {@link SourceActor} / {@link FilterActor}。
 * <p>
 * 端口条目的匹配依据是输入端口的约束集合（{@link Node.InPort#getConstraint()}，
 * 交叉类型语义）与输出端口声明的类型，而不是端口的具体实现类。
 */
public final class CardWidgetsRegistry {
    private CardWidgetsRegistry() {
    }

    private record InEntry(Class<?> type, BiFunction<Node.InPort<?>, Source<?>, Actor> factory) {
    }

    private record OutEntry(Class<?> type, Function<Node.OutPort<?>, Actor> factory) {
    }

    private static final List<InEntry> IN_ENTRIES = new ArrayList<>();
    private static final List<OutEntry> OUT_ENTRIES = new ArrayList<>();

    static {
        registerIn(NumFrame.class, NumPortEditor::new);
        registerIn(String.class, TextPortEditor::new);
        registerOut(NumFrame.class, NumOutputRow::new);
    }

    /** 注册输入端口的 widget 工厂。目标类型为端口约束需能容纳的类型。 */
    public static void registerIn(Class<?> type, BiFunction<Node.InPort<?>, Source<?>, Actor> factory) {
        IN_ENTRIES.add(new InEntry(type, factory));
    }

    /** 注册输出端口的只读显示工厂。目标类型为端口声明类型的父类。 */
    public static void registerOut(Class<?> type, Function<Node.OutPort<?>, Actor> factory) {
        OUT_ENTRIES.add(new OutEntry(type, factory));
    }

    /** 为输入端口创建编辑 widget；没有注册对应类型的返回 null（该端口不显示）。 */
    public static Actor createEditor(Node.InPort<?> port, Source<?> source) {
        for (InEntry entry : IN_ENTRIES) {
            if (accepts(port, entry.type())) {
                return entry.factory().apply(port, source);
            }
        }
        return null;
    }

    /** 为输出端口创建只读显示行；没有注册对应类型的返回 null（该端口不显示）。 */
    public static Actor createOutputRow(Node.OutPort<?> port) {
        Class<?> type = port.getType();
        if (type == null) return null;
        for (OutEntry entry : OUT_ENTRIES) {
            if (entry.type().isAssignableFrom(type)) {
                return entry.factory().apply(port);
            }
        }
        return null;
    }

    /** 端口约束为交叉类型：目标类型必须满足全部约束。 */
    private static boolean accepts(Node.InPort<?> port, Class<?> type) {
        for (Class<?> c : port.getConstraint()) {
            if (!c.isAssignableFrom(type)) {
                return false;
            }
        }
        return true;
    }
}
