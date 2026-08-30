package com.lomekwi.cave.pipeline;

import com.lomekwi.cave.pipeline.image.TransNode;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * 过滤器注册表：按源帧类型动态匹配可用的 {@link Filter}。
 */
public class FilterRegistry {
    private final List<Class<? extends Filter<?>>> entries = new ArrayList<>();

    public FilterRegistry() {
        register(TransNode.class);
    }

    public void register(Class<? extends Filter<?>> filterClass) {
        entries.add(filterClass);
    }

    public int getCompatibleCount(Source<?> source) {
        Class<?> frameType = source.getFrameType();
        int count = 0;
        for (Class<? extends Filter<?>> filterClass : entries) {
            if (targetTypeOf(filterClass).isAssignableFrom(frameType)) count++;
        }
        return count;
    }

    public Filter<?> createCompatible(Source<?> source, int index) {
        Class<?> frameType = source.getFrameType();
        int count = 0;
        for (Class<? extends Filter<?>> filterClass : entries) {
            if (targetTypeOf(filterClass).isAssignableFrom(frameType)) {
                if (count == index) return create(filterClass);
                count++;
            }
        }
        return null;
    }

    private static Filter<?> create(Class<? extends Filter<?>> filterClass) {
        try {
            Constructor<?> ctor = filterClass.getConstructor();
            return (Filter<?>) ctor.newInstance();
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException(filterClass.getName() + " 缺少无参构造器", e);
        } catch (InvocationTargetException | InstantiationException | IllegalAccessException e) {
            throw new RuntimeException("创建过滤器 " + filterClass.getName() + " 失败", e);
        }
    }

    private static Class<?> targetTypeOf(Class<? extends Filter<?>> filterClass) {
        Type superclass = filterClass.getGenericSuperclass();
        if (superclass instanceof ParameterizedType pt) {
            Type arg = pt.getActualTypeArguments()[0];
            if (arg instanceof Class<?> clazz) {
                return clazz;
            }
        }
        throw new IllegalArgumentException("无法从 " + filterClass.getName() + " 推断过滤器目标类型");
    }
}