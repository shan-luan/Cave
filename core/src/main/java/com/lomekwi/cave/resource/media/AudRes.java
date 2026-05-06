package com.lomekwi.cave.resource.media;

import com.lomekwi.cave.resource.decoder.AudDecRes;
import com.lomekwi.cave.resource.decoder.DecRes;

public class AudRes extends MedRes{
    private static final long serialVersionUID = 1L;

    /**
     * 必须确保路径对应一个存在的文件
     */
    public AudRes(String path) {
        super(path);
    }

    @Override
    protected DecRes newDecoder() {
        return new AudDecRes(this);
    }
}
