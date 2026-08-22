package com.lomekwi.cave.pipeline;

import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * 模型暴露的可显示/可修改条目描述。由 {@link Source} 和 {@link Modifier}
 * 通过 {@code getParams()} 提供，UI 层据此自动生成 widget。
 */
public abstract class Param<T> {
    public enum UndoMode { NONE, IMMEDIATE, BATCHED }

    public final String label;
    public UndoMode undoMode = UndoMode.NONE;

    protected Param(String label) {
        this.label = label;
    }

    public abstract T get();

    public abstract void set(T value);

    public Param<T> undo(UndoMode mode) {
        this.undoMode = mode;
        return this;
    }

    /** 只读信息行。 */
    public static final class Info extends Param<String> {
        private final Supplier<String> getter;

        public Info(String label, Supplier<String> getter) {
            super(label);
            this.getter = getter;
        }

        @Override
        public String get() {
            return getter.get();
        }

        @Override
        public void set(String value) {
            throw new UnsupportedOperationException("Info param is read-only");
        }
    }

    /** 浮点数值（基于 Modifier.Val）。 */
    public static final class FloatSpin extends Param<Double> {
        public final Modifier.Val val;
        public final double min, max, step;
        public final int decimals;

        public FloatSpin(String label, Modifier.Val val, double min, double max, double step, int decimals) {
            super(label);
            this.val = val;
            this.min = min;
            this.max = max;
            this.step = step;
            this.decimals = decimals;
        }

        @Override
        public Double get() {
            return val.get();
        }

        @Override
        public void set(Double value) {
            val.set(value);
        }
    }

    /** 整数数值。 */
    public static final class IntSpin extends Param<Integer> {
        private final IntSupplier getter;
        private final IntConsumer setter;
        public final int min, max, step;

        public IntSpin(String label, IntSupplier getter, IntConsumer setter, int min, int max, int step) {
            super(label);
            this.getter = getter;
            this.setter = setter;
            this.min = min;
            this.max = max;
            this.step = step;
        }

        @Override
        public Integer get() {
            return getter.getAsInt();
        }

        @Override
        public void set(Integer value) {
            setter.accept(value);
        }
    }

    /** 布尔开关。 */
    public static final class BoolCheck extends Param<Boolean> {
        private final Supplier<Boolean> getter;
        private final Consumer<Boolean> setter;

        public BoolCheck(String label, Supplier<Boolean> getter, Consumer<Boolean> setter) {
            super(label);
            this.getter = getter;
            this.setter = setter;
        }

        @Override
        public Boolean get() {
            return getter.get();
        }

        @Override
        public void set(Boolean value) {
            setter.accept(value);
        }
    }

    /** 文本输入，multiline 时为多行文本域。 */
    public static final class TextInput extends Param<String> {
        private final Supplier<String> getter;
        private final Consumer<String> setter;
        public final boolean multiline;

        public TextInput(String label, Supplier<String> getter, Consumer<String> setter, boolean multiline) {
            super(label);
            this.getter = getter;
            this.setter = setter;
            this.multiline = multiline;
        }

        @Override
        public String get() {
            return getter.get();
        }

        @Override
        public void set(String value) {
            setter.accept(value);
        }
    }

    /** 文件路径选择。 */
    public static final class FileSelect extends Param<String> {
        private final Supplier<String> getter;
        private final Consumer<String> setter;
        public final String dialogTitle;
        public final String[] extensions;

        public FileSelect(String label, String dialogTitle, String[] extensions,
                          Supplier<String> getter, Consumer<String> setter) {
            super(label);
            this.dialogTitle = dialogTitle;
            this.extensions = extensions;
            this.getter = getter;
            this.setter = setter;
        }

        @Override
        public String get() {
            return getter.get();
        }

        @Override
        public void set(String value) {
            setter.accept(value);
        }
    }

    /** 单选项组（索引制），渲染为一排切换按钮。 */
    public static final class Choice extends Param<Integer> {
        private final IntSupplier getter;
        private final IntConsumer setter;
        public final String[] options;

        public Choice(String label, String[] options, IntSupplier getter, IntConsumer setter) {
            super(label);
            this.options = options;
            this.getter = getter;
            this.setter = setter;
        }

        @Override
        public Integer get() {
            return getter.getAsInt();
        }

        @Override
        public void set(Integer value) {
            setter.accept(value);
        }
    }
}
