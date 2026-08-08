package com.lomekwi.cave.pipeline;

import com.lomekwi.cave.pipeline.image.AlignModifier;
import com.lomekwi.cave.pipeline.image.TransModifier;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class ModifierRegistry {
    private final List<Class<? extends Modifier<?>>> entries = new ArrayList<>();

    public ModifierRegistry() {
        register(TransModifier.class);
        register(AlignModifier.class);
    }

    public void register(Class<? extends Modifier<?>> modifierClass) {
        entries.add(modifierClass);
    }

    public int getCompatibleCount(Source<?> source) {
        Class<?> frameType = source.getFrameType();
        int count = 0;
        for (Class<? extends Modifier<?>> modifierClass : entries) {
            if (targetTypeOf(modifierClass).isAssignableFrom(frameType)) count++;
        }
        return count;
    }

    public Modifier<?> createCompatible(Source<?> source, int index) {
        Class<?> frameType = source.getFrameType();
        int count = 0;
        for (Class<? extends Modifier<?>> modifierClass : entries) {
            if (targetTypeOf(modifierClass).isAssignableFrom(frameType)) {
                if (count == index) return create(modifierClass, source);
                count++;
            }
        }
        return null;
    }

    private static Modifier<?> create(Class<? extends Modifier<?>> modifierClass, Source<?> source) {
        try {
            Constructor<?> ctor = modifierClass.getConstructor(Source.class);
            return (Modifier<?>) ctor.newInstance(source);
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException(modifierClass.getName() + " 缺少接收 Source 的构造器", e);
        } catch (InvocationTargetException | InstantiationException | IllegalAccessException e) {
            throw new RuntimeException("创建修改器 " + modifierClass.getName() + " 失败", e);
        }
    }

    private static Class<?> targetTypeOf(Class<? extends Modifier<?>> modifierClass) {
        Type superclass = modifierClass.getGenericSuperclass();
        if (superclass instanceof ParameterizedType pt) {
            Type arg = pt.getActualTypeArguments()[0];
            if (arg instanceof Class<?> clazz) {
                return clazz;
            }
        }
        throw new IllegalArgumentException("无法从 " + modifierClass.getName() + " 推断修改器目标类型");
    }
}
