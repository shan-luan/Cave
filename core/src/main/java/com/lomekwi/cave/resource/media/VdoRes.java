package com.lomekwi.cave.resource.media;

import com.lomekwi.cave.resource.decoder.VdoDecRes;

import java.io.FileNotFoundException;
import java.util.Set;

public class VdoRes extends MedRes {
    private final int width;
    private final int height;
    private static final long serialVersionUID = 1L;
    public VdoRes(String path){
        super(path);
        width=((VdoDecRes)decRes).getWidth();
        height=((VdoDecRes)decRes).getHeight();
    }
    @Override
    public VdoDecRes getDecoder() {
        return (VdoDecRes) decRes;
    }
    public int getWidth(){
        return width;
    }
    public int getHeight(){
        return height;
    }
    @Override
    protected void setupDecoders() {
        decRes = new VdoDecRes(this);
    }
}
