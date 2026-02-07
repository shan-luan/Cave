package com.lomekwi.cine.resource;

import com.lomekwi.cine.pipeline.decode.DecProc;

/**
 *媒体资源类，指代一个在磁盘中存在，占有编解码器的资源
 */
public abstract class MedRes {
    private final String path;
    protected DecProc decProc;

    public MedRes(String path) {
        this.path = path;
    }

    public String getPath() {
        return path;
    }
    public DecProc getDecoder(){
        return decProc;
    };
}
