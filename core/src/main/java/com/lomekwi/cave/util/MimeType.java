package com.lomekwi.cave.util;

import static com.lomekwi.cave.util.i18n.I18N.i18n;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public final class MimeType {
    private MimeType() {
    }

    private static final Map<String, String> extensionToMimeType = new HashMap<>();
    
    static {
        // 视频格式
        extensionToMimeType.put("mkv", "video/x-matroska");
        extensionToMimeType.put("mp4", "video/mp4");
        extensionToMimeType.put("avi", "video/x-msvideo");
        extensionToMimeType.put("mov", "video/quicktime");
        extensionToMimeType.put("wmv", "video/x-ms-wmv");
        extensionToMimeType.put("flv", "video/x-flv");
        extensionToMimeType.put("webm", "video/webm");
        extensionToMimeType.put("m4v", "video/x-m4v");
        extensionToMimeType.put("mpeg", "video/mpeg");
        extensionToMimeType.put("mpg", "video/mpeg");
        extensionToMimeType.put("3gp", "video/3gpp");
        
        // 音频格式
        extensionToMimeType.put("mp3", "audio/mpeg");
        extensionToMimeType.put("wav", "audio/wav");
        extensionToMimeType.put("flac", "audio/flac");
        extensionToMimeType.put("aac", "audio/aac");
        extensionToMimeType.put("ogg", "audio/ogg");
        extensionToMimeType.put("wma", "audio/x-ms-wma");
        extensionToMimeType.put("m4a", "audio/x-m4a");
    }

    /**
     * 检测文件的MIME类型，优先使用系统检测，失败时使用扩展名匹配
     * @param file 要检测的文件
     * @return MIME类型字符串，如果无法检测则返回null
     */
    public static String detectMimeType(File file) {
        if (file == null || !file.exists()) {
            return null;
        }
        
        Path path = file.toPath();
        
        // 首先尝试使用系统检测
        try {
            String systemMimeType = Files.probeContentType(path);
            if (systemMimeType != null && !systemMimeType.isEmpty()) {
                return systemMimeType;
            }
        } catch (Exception e) {
            // 系统检测失败，继续使用扩展名检测
        }
        
        // 使用文件扩展名进行匹配
        String fileName = file.getName().toLowerCase();
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < fileName.length() - 1) {
            String extension = fileName.substring(lastDotIndex + 1);
            String mimeType = extensionToMimeType.get(extension);
            if (mimeType != null) {
                return mimeType;
            }
        }
        
        return null;
    }

    /**
     * 获取type/{@code *}形式的通配MIME类型
     * @param mimeType 完整的MIME类型，如 "video/mp4"
     * @return type/{@code *}形式，如 "video/{@code *}"
     */
    public static String getTypeWildcard(String mimeType) {
        if (mimeType == null || mimeType.isEmpty()) {
            throw new IllegalArgumentException(i18n("MIME类型不能为空"));
        }
        
        int slashIndex = mimeType.indexOf('/');
        if (slashIndex == -1) {
            throw new IllegalArgumentException(i18n("无效的MIME类型格式: ") + mimeType);
        }
        
        return mimeType.substring(0, slashIndex) + "/*";
    }

    /**
     * 获取{@code *}/subtype形式的通配MIME类型
     * @param mimeType 完整的MIME类型，如 "video/mp4"
     * @return {@code *}/subtype形式，如 "{@code *}/mp4"
     */
    public static String getSubtypeWildcard(String mimeType) {
        if (mimeType == null || mimeType.isEmpty()) {
            throw new IllegalArgumentException(i18n("MIME类型不能为空"));
        }
        
        int slashIndex = mimeType.indexOf('/');
        if (slashIndex == -1) {
            throw new IllegalArgumentException(i18n("无效的MIME类型格式: ") + mimeType);
        }
        
        return "*/" + mimeType.substring(slashIndex + 1);
    }

    /**
     * 获取{@code *}/{@code *}通配MIME类型
     * @return "{@code *}/{@code *}"
     */
    public static String getAllWildcard() {
        return "*/*";
    }
}
