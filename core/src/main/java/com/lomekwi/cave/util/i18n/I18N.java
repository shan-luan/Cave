package com.lomekwi.cave.util.i18n;

public class I18N {
    //TODO
    public static String i18n(String key) {
        return key;
    }
    public static String[] i18n(String... keys) {
        for(int i=0; i<keys.length; i++){
            keys[i] = i18n(keys[i]);
        }
        return keys;
    }
}
