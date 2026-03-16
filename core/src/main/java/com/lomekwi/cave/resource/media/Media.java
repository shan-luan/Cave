package com.lomekwi.cave.resource.media;

import com.badlogic.gdx.files.FileHandle;
import com.lomekwi.cave.util.FileNameUtil;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public final class Media {
    private static final Map<String, Function<String,MedRes>> MediaExtensions;
    static {
        MediaExtensions = new HashMap<>();
        register(Set.of("mp4","mov","avi"), VdoRes::new);
    }
    public static MedRes create(File file){
        return MediaExtensions.get(FileNameUtil.getExtension(file)).apply(file.getPath());
    }
    public static void register(Set<String> extensions, Function<String,MedRes> constructor){
        extensions.forEach(e->MediaExtensions.put(e,constructor));
    }
}
