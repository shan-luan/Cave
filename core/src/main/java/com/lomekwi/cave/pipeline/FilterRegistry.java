package com.lomekwi.cave.pipeline;

import com.lomekwi.cave.pipeline.image.AlignFilter;
import com.lomekwi.cave.pipeline.image.TransFilter;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class FilterRegistry {
    private final List<Class<? extends Filter<?>>> entries = new ArrayList<>();

    public FilterRegistry() {
        register(TransFilter.class);
        register(AlignFilter.class);
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
                if (count == index) return create(filterClass, source);
                count++;
            }
        }
        return null;
    }

    private static Filter<?> create(Class<? extends Filter<?>> filterClass, Source<?> source) {
        try {
            Constructor<?> ctor = filterClass.getConstructor(Source.class);
            return (Filter<?>) ctor.newInstance(source);
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException(filterClass.getName() + " 缺少接收 Source 的构造器", e);
        } catch (InvocationTargetException | InstantiationException | IllegalAccessException e) {
            throw new RuntimeException("创建滤镜 " + filterClass.getName() + " 失败", e);
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
        throw new IllegalArgumentException("无法从 " + filterClass.getName() + " 推断滤镜目标类型");
    }
}