package com.lomekwi.cave.resource.media;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public final class Media {
    private Media(){
    }
    private static final Map<String, Function<String, MedRes>> map = new HashMap<>(Map.of(
        "video/mp4", VdoRes::new
    ));
    public static MedRes create(String mimeType, String path){
        return map.get(mimeType).apply(path);
    }
    public static boolean isSupported(String mimeType){
        return map.containsKey(mimeType);
    }
    public static void register(String mimeType, Function<String, MedRes> constructor){
        map.put(mimeType,constructor);
    }
    public static void unregister(String mimeType){
        map.remove(mimeType);
    }
}
