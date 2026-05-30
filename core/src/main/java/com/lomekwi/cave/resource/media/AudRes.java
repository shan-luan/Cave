package com.lomekwi.cave.resource.media;

import com.lomekwi.cave.resource.decoder.AudDecRes;

import java.io.Serial;

public class AudRes extends MedRes{
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 必须确保路径对应一个存在的文件
     */
    public AudRes(String path) {
        super(path);
    }

    @Override
    protected AudDecRes newDecoder() {
        return new AudDecRes(this);
    }
}
