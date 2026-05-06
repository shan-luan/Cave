package com.lomekwi.cave.resource.media;

import com.lomekwi.cave.util.MimeType;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public final class Media {
    private Media(){
    }
    private static final Map<String, Function<String, MedRes>> map = new HashMap<>();
    static {
        map.put("video/*", VdoRes::new);
        map.put("audio/*", AudRes::new);
    }
    public static MedRes create(String mimeType, String path){
        Function<String, MedRes> constructor = findConstructor(mimeType);
        if (constructor == null) {
            throw new IllegalArgumentException("Unsupported mime type: " + mimeType);
        }
        return constructor.apply(path);
    }
    private static Function<String, MedRes> findConstructor(String mimeType) {
        Function<String, MedRes> constructor = map.get(mimeType);
        if (constructor != null) {
            return constructor;
        }

        String typeWildcard = MimeType.getTypeWildcard(mimeType);
        return map.get(typeWildcard);
    }
    public static boolean isSupported(String mimeType){
        return findConstructor(mimeType) != null;
    }
    public static void register(String mimeType, Function<String, MedRes> constructor){
        map.put(mimeType, constructor);
    }
    public static void unregister(String mimeType){
        map.remove(mimeType);
    }
}
