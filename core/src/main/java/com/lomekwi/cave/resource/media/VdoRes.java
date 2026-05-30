package com.lomekwi.cave.resource.media;

import com.lomekwi.cave.resource.decoder.DecRes;
import com.lomekwi.cave.resource.decoder.VdoDecRes;

import java.io.Serial;

public class VdoRes extends MedRes {
    private int width;
    private int height;
    private long frameLength;
    @Serial
    private static final long serialVersionUID = 1L;
    public VdoRes(String path){
        super(path);
    }
    @Override
    public VdoDecRes getDecoder(int trackIndex) {
        return (VdoDecRes) super.getDecoder(trackIndex);
    }
    public int getWidth(){
        return width;
    }
    public int getHeight(){
        return height;
    }
    @Override
    protected void generateMetadata(DecRes<?> metadataDecRes) {
        VdoDecRes vdr = (VdoDecRes) metadataDecRes;
        width = vdr.getWidth();
        height = vdr.getHeight();
        frameLength = vdr.getLengthPerFrame();
    }

    public long getFrameLength() {
        return frameLength;
    }

    @Override
    protected VdoDecRes newDecoder() {
        return new VdoDecRes(this);
    }
}
