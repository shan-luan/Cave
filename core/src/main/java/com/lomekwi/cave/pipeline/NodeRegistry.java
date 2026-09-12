package com.lomekwi.cave.pipeline;

import com.lomekwi.cave.pipeline.image.TransNode;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * 通用节点注册表：注册任意 {@link Node} 子类，并按目标帧类型动态匹配可用的节点。
 *
 * <p>兼容性规则：节点是 {@link Filter} 时，看它泛型声明的目标帧类型是否
 * {@code isAssignableFrom} 源帧类型；非 Filter 的普通节点视为始终兼容。</p>
 */
public class NodeRegistry {
    private final List<Class<? extends Node>> entries = new ArrayList<>();

    public NodeRegistry() {
        register(TransNode.class);
        register(NodeGraphFilter.class);
    }

    public void register(Class<? extends Node> nodeClass) {
        entries.add(nodeClass);
    }

    public int getCompatibleCount(Source<?> source) {
        Class<?> frameType = source.getFrameType();
        int count = 0;
        for (Class<? extends Node> nodeClass : entries) {
            if (isCompatible(nodeClass, frameType)) count++;
        }
        return count;
    }

    /**
     * 创建第 index 个兼容节点。返回类型按 Filter 使用方约定——非 Filter 节点
     * 目前没有消费方，Inspector 只会把结果加入 filter 链。
     */
    public Node createCompatible(Source<?> source, int index) {
        Class<?> frameType = source.getFrameType();
        int count = 0;
        for (Class<? extends Node> nodeClass : entries) {
            if (isCompatible(nodeClass, frameType)) {
                if (count == index) return create(nodeClass);
                count++;
            }
        }
        return null;
    }

    private static Node create(Class<? extends Node> nodeClass) {
        try {
            Constructor<?> ctor = nodeClass.getConstructor();
            Node node = (Node) ctor.newInstance();
            return node;
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException(nodeClass.getName() + " 缺少无参构造器", e);
        } catch (InvocationTargetException | InstantiationException | IllegalAccessException e) {
            throw new RuntimeException("创建节点 " + nodeClass.getName() + " 失败", e);
        }
    }

    private static boolean isCompatible(Class<? extends Node> nodeClass, Class<?> frameType) {
        if (!Filter.class.isAssignableFrom(nodeClass)) return true; // 非 Filter 节点始终兼容
        @SuppressWarnings("unchecked")
        Class<? extends Filter<?>> asFilter = (Class<? extends Filter<?>>) nodeClass.asSubclass((Class) Filter.class);
        return targetTypeOf(asFilter).isAssignableFrom(frameType);
    }

    private static Class<?> targetTypeOf(Class<? extends Filter<?>> filterClass) {
        Type superclass = filterClass.getGenericSuperclass();
        if (superclass instanceof ParameterizedType pt) {
            Type arg = pt.getActualTypeArguments()[0];
            if (arg instanceof Class<?> clazz) {
                return clazz;
            }
        }
        throw new IllegalArgumentException("无法从 " + filterClass.getName() + " 推断目标类型");
    }
}
