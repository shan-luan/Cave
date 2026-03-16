package com.lomekwi.cave.util;

import java.io.File;

public final class FileNameUtil {
    private FileNameUtil(){}
    public static String getExtension(File file) {
        if (file == null) return "";
        String name = file.getName();
        int dotIndex = name.lastIndexOf('.');
        return (dotIndex >= 0 && dotIndex < name.length() - 1) ? name.substring(dotIndex + 1) : "";
    }
}
