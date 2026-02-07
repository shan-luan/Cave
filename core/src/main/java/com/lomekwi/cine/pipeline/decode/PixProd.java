package com.lomekwi.cine.pipeline.decode;

import com.lomekwi.cine.pipeline.Processor;
import com.lomekwi.cine.pipeline.Product;
import com.lomekwi.cine.pipeline.upload.TexUpldProc;

import java.nio.ByteBuffer;

public class PixProd implements Product,AutoCloseable {
    private ByteBuffer pixels;
    private final int width;
    private final int height;

    public PixProd(int width, int height) {
        this.width = width;
        this.height = height;
    }

    @Override
    public Processor getNextProcessor() {
        return TexUpldProc.INSTANCE;
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
    @Override
    public void close() {
        TexUpldProc.INSTANCE.remove(this);
    }
}
