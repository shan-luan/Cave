package com.lomekwi.cave.resource.media;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public final class Media {
    private Media(){
    }
    private static final Map<String, Function<String, MedRes>> map = new HashMap<>();
    static {
        map.put("video/mp4", VdoRes::new);
    }
    public static MedRes create(String mimeType, String path){
        Function<String, MedRes> constructor = map.get(mimeType);
        if (constructor == null) {
            throw new IllegalArgumentException("Unsupported mime type: " + mimeType);
        }
        return constructor.apply(path);
    }
    public static boolean isSupported(String mimeType){
        return map.containsKey(mimeType);
    }
    public static void register(String mimeType, Function<String, MedRes> constructor){
        map.put(mimeType, constructor);
    }
    public static void unregister(String mimeType){
        map.remove(mimeType);
    }
}
