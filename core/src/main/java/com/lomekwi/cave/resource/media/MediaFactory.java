package com.lomekwi.cave.resource.media;

import com.lomekwi.cave.util.MimeType;
import org.bytedeco.javacv.FFmpegFrameGrabber;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class MediaFactory {
    private MediaFactory(){
    }
    private static final Map<String, Function<String, MedRes>> map = new HashMap<>();
    static {
        map.put("video/*", VdoRes::new);
        map.put("audio/*", AudRes::new);
        map.put("image/*", ImgRes::new);
    }
    public static MedRes create(String mimeType, String path){
        Function<String, MedRes> constructor = findConstructor(mimeType);
        if (constructor == null) {
            throw new IllegalArgumentException("Unsupported mime type: " + mimeType);
        }
        return constructor.apply(path);
    }

    /**
     * 为一个文件创建所有可用的媒体资源。
     * 视频文件如果包含音频流，会额外创建 AudRes。
     */
    public static List<MedRes> createAll(String mimeType, String path) {
        List<MedRes> results = new ArrayList<>();

        String typeWildcard = MimeType.getTypeWildcard(mimeType);
        Function<String, MedRes> constructor = findConstructor(mimeType);

        if (constructor == null) {
            throw new IllegalArgumentException("Unsupported mime type: " + mimeType);
        }

        // 主资源
        results.add(constructor.apply(path));

        // 视频文件如果包含音频流，额外创建 AudRes
        if (typeWildcard.equals("video/*") && hasAudioStream(path)) {
            results.add(new AudRes(path));
        }

        return results;
    }

    /**
     * 轻量探测文件是否包含音频流
     */
    private static boolean hasAudioStream(String path) {
        try (FFmpegFrameGrabber g = new FFmpegFrameGrabber(path)) {
            g.start();
            return g.getAudioChannels() > 0;
        } catch (Exception e) {
            return false;
        }
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
