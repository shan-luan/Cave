package com.lomekwi.cave.pipeline;

import com.lomekwi.cave.pipeline.image.Transformable;
import com.lomekwi.cave.pipeline.image.TransFilter;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class FilterRegistry {
    private static final List<Entry> entries = new ArrayList<>();

    private record Entry(String name, Class<?> targetType, Function<Source<?>, Filter<?>> factory) {}

    static {
        register("变换滤镜", Transformable.class, s -> new TransFilter(s, 0, 0, 1, 1, 0));
    }

    public static void register(String displayName, Class<?> targetType, Function<Source<?>, Filter<?>> factory) {
        entries.add(new Entry(displayName, targetType, factory));
    }

    public static int getCompatibleCount(Source<?> source) {
        Class<?> frameType = source.getFrameType();
        int count = 0;
        for (Entry e : entries) {
            if (e.targetType().isAssignableFrom(frameType)) count++;
        }
        return count;
    }

    public static String getCompatibleName(Source<?> source, int index) {
        Class<?> frameType = source.getFrameType();
        int count = 0;
        for (Entry e : entries) {
            if (e.targetType().isAssignableFrom(frameType)) {
                if (count == index) return e.name();
                count++;
            }
        }
        return null;
    }

    public static Filter<?> createCompatible(Source<?> source, int index) {
        Class<?> frameType = source.getFrameType();
        int count = 0;
        for (Entry e : entries) {
            if (e.targetType().isAssignableFrom(frameType)) {
                if (count == index) return e.factory().apply(source);
                count++;
            }
        }
        return null;
    }
}
