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
    public static String ensureExtension(String name, String ext) {
        if (name == null || ext == null) return name;
        String dotExt = ext.startsWith(".") ? ext : "." + ext;
        return name.toLowerCase().endsWith(dotExt.toLowerCase()) ? name : name + dotExt;
    }
    public static File ensureExtension(File file, String ext) {
        if (file == null) return null;
        String name = file.getName();
        String newName = ensureExtension(name, ext);
        return name.equals(newName) ? file : new File(file.getParent(), newName);
    }
}
