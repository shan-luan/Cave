package com.lomekwi.cave.resource.media;

import com.lomekwi.cave.resource.decoder.VdoDecRes;

import java.io.Serial;

public class VdoRes extends MedRes {
    private final int width;
    private final int height;
    @Serial
    private static final long serialVersionUID = 1L;
    public VdoRes(String path){
        super(path);
        width=((VdoDecRes)metadataDecRes).getWidth();
        height=((VdoDecRes)metadataDecRes).getHeight();
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
    protected VdoDecRes newDecoder() {
        return new VdoDecRes(this);
    }
}
