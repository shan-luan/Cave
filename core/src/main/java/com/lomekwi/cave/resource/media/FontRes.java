package com.lomekwi.cave.resource.media;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.utils.IntMap;
import com.lomekwi.cave.resource.Resource;

import java.io.Serial;
import java.io.Serializable;

public class FontRes implements Resource, Serializable {
    private final String path;
    private transient FreeTypeFontGenerator generator;
    private transient IntMap<BitmapFont> fontCache;

    @Serial
    private static final long serialVersionUID = 1L;

    public FontRes(String path) {
        this.path = path;
    }

    public BitmapFont getFont(int size) {
        if (fontCache == null) fontCache = new IntMap<>();
        BitmapFont cached = fontCache.get(size);
        if (cached != null) return cached;
        if (generator == null) {
            var handle = Gdx.files.internal(path);
            if (!handle.exists()) {
                handle = Gdx.files.absolute(path);
            }
            generator = new FreeTypeFontGenerator(handle);
        }
        FreeTypeFontGenerator.FreeTypeFontParameter param =
            new FreeTypeFontGenerator.FreeTypeFontParameter();
        param.size = size;
        param.incremental = true;
        BitmapFont font = generator.generateFont(param);
        fontCache.put(size, font);
        return font;
    }

    public String getPath() {
        return path;
    }

    @Override
    public void close() {
        if (fontCache != null) {
            for (BitmapFont font : fontCache.values()) {
                font.dispose();
            }
            fontCache.clear();
        }
        if (generator != null) {
            generator.dispose();
            generator = null;
        }
    }
}
