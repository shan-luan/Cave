package com.lomekwi.cine.resource;

import com.lomekwi.cine.pipeline.decode.DecProc;

/**
 *媒体类，指代一个在磁盘中存在，占有编解码器的资源
 */
public abstract class Media {
    private final String path;
    protected DecProc decProc;

    public Media(String path) {
        this.path = path;
    }

    public String getPath() {
        return path;
    }
    public DecProc getDecoder(){
        return decProc;
    };
}
