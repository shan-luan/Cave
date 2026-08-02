package com.lomekwi.cave.resource.media;

import com.lomekwi.cave.util.MimeType;
import org.bytedeco.javacv.FFmpegFrameGrabber;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class MediaFactory {
    private final Map<String, Function<String, MedRes>> map = new HashMap<>();

    public MediaFactory() {
        map.put("video/*", VdoRes::new);
        map.put("audio/*", AudRes::new);
        map.put("image/*", ImgRes::new);
    }

    public MedRes create(String mimeType, String path){
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
    public List<MedRes> createAll(String mimeType, String path) {
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

    private Function<String, MedRes> findConstructor(String mimeType) {
        Function<String, MedRes> constructor = map.get(mimeType);
        if (constructor != null) {
            return constructor;
        }

        String typeWildcard = MimeType.getTypeWildcard(mimeType);
        return map.get(typeWildcard);
    }
    public boolean isSupported(String mimeType){
        if (mimeType == null) return false;
        return findConstructor(mimeType) != null;
    }
    public void register(String mimeType, Function<String, MedRes> constructor){
        map.put(mimeType, constructor);
    }
    public void unregister(String mimeType){
        map.remove(mimeType);
    }
}