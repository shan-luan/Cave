package com.lomekwi.cave.util.i18n;

public class I18N {
    //TODO:完成
    public static String get(String key) {
        return key;
    }
    public static String[] get(String... keys) {
        for(int i=0; i<keys.length; i++){
            keys[i] = get(keys[i]);
        }
        return keys;
    }
}
