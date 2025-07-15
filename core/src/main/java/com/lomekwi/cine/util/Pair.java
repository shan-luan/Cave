package com.lomekwi.cine.util;

import java.util.Objects;

/**
 * 一个不可变的包含两个泛型值的容器类
 * @param <A> 第一个泛型类型
 * @param <B> 第二个泛型类型
 */
public final class Pair<A, B> {
    private final A first;
    private final B second;

    /**
     * 构造函数
     * @param first 第一个值
     * @param second 第二个值
     */
    public Pair(A first, B second) {
        this.first = first;
        this.second = second;
    }

    /**
     * 获取第一个值
     * @return 第一个值
     */
    public A getFirst() {
        return first;
    }

    /**
     * 获取第二个值
     * @return 第二个值
     */
    public B getSecond() {
        return second;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Pair<?, ?> pair = (Pair<?, ?>) o;
        return Objects.equals(first, pair.first) &&
            Objects.equals(second, pair.second);
    }

    @Override
    public int hashCode() {
        return Objects.hash(first, second);
    }

    @Override
    public String toString() {
        return "Pair{" + first + ", " + second + '}';
    }

    /**
     * 静态工厂方法，方便创建实例
     * @param first 第一个值
     * @param second 第二个值
     * @return 新的Pair实例
     * @param <A> 第一个泛型类型
     * @param <B> 第二个泛型类型
     */
    public static <A, B> Pair<A, B> of(A first, B second) {
        return new Pair<>(first, second);
    }
}
