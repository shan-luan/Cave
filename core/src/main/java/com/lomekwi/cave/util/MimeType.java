package com.lomekwi.cave.util;

import static com.lomekwi.cave.util.i18n.I18N.i18n;

public final class MimeType {
    private MimeType() {
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
