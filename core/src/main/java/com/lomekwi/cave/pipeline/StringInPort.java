package com.lomekwi.cave.pipeline;

import java.util.Set;

/**
 * 字符串输入端口：数据为 {@link String}，约束为 String.class。
 * 对应 UI 中绑定文本输入区域 widget。
 */
public class StringInPort extends Node.InPort<String> {

    public StringInPort(String name) {
        this(name, "");
    }

    public StringInPort(String name, String defaultValue) {
        super(name);
        setDefaultData(defaultValue);
    }

    @Override
    public Set<Class<?>> getConstraint() {
        return Set.of(String.class);
    }
}
