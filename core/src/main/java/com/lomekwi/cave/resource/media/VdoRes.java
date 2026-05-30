package com.lomekwi.cave.resource.media;

import com.lomekwi.cave.resource.decoder.VdoDecRes;
import com.lomekwi.cave.timeline.Track;

import java.io.Serial;

public class VdoRes extends MedRes {
    private final int width;
    private final int height;
    @Serial
    private static final long serialVersionUID = 1L;
    public VdoRes(String path){
        super(path);
        width=((VdoDecRes)decRes.get(null)).getWidth();
        height=((VdoDecRes)decRes.get(null)).getHeight();
    }
    @Override
    public VdoDecRes getDecoder(Track track) {
        return (VdoDecRes) super.getDecoder(track);
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
