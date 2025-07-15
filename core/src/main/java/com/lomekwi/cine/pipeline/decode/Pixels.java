package com.lomekwi.cine.pipeline.decode;

import com.lomekwi.cine.pipeline.Processor;
import com.lomekwi.cine.pipeline.Product;
import com.lomekwi.cine.pipeline.upload.TextureUploader;

import java.nio.ByteBuffer;

public class Pixels implements Product {
    private ByteBuffer pixels;
    private final int width;
    private final int height;

    public Pixels(int width, int height) {
        this.width = width;
        this.height = height;
    }

    @Override
    public Processor getNextProcessor() {
        return TextureUploader.INSTANCE;
    }

    public ByteBuffer getPixels() {
        return pixels;
    }

    public void setPixels(ByteBuffer pixels) {
        this.pixels = pixels;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }
    public void dispose() {
        TextureUploader.INSTANCE.remove(this);
    }
}
