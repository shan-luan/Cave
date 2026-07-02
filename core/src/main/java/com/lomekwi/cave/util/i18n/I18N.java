package com.lomekwi.cave.util.i18n;

@Deprecated
public class I18N {
    //TODO
    @Deprecated
    public static String i18n(String key) {
        return key;
    }
    @Deprecated
    public static String[] i18n(String... keys) {
        for(int i=0; i<keys.length; i++){
            keys[i] = i18n(keys[i]);
        }
        return keys;
    }
}
