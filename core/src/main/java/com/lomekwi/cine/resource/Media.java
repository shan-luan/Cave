package com.lomekwi.cine.resource;

import com.lomekwi.cine.pipeline.decode.Decoder;

import java.util.HashMap;

/**
 *媒体类，指代一个在磁盘中存在，占有编解码器的资源
 */
public abstract class Media<T extends Media<T>> {
    private final String path;
    protected Decoder<T> decoder;

    public Media(String path) {
        this.path = path;
    }

    public String getPath() {
        return path;
    }
    public Decoder<T> getDecoder(){
        return decoder;
    };
}
