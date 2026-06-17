package com.lomekwi.cave.resource.media;

import com.lomekwi.cave.resource.decoder.AudDecRes;
import com.lomekwi.cave.resource.decoder.DecRes;

import java.io.Serial;

public class AudRes extends MedRes{
    @Serial
    private static final long serialVersionUID = 1L;
    private long frameLength;

    /**
     * 必须确保路径对应一个存在的文件
     */
    public AudRes(String path) {
        super(path);
    }

    @Override
    protected void generateMetadata(DecRes<?> metadataDecRes) {
        frameLength= metadataDecRes.getLengthPerFrame();

    }

    @Override
    protected AudDecRes newDecoder() {
        return new AudDecRes(this);
    }

    public long getFrameLength() {
        return frameLength;
    }
}
