package com.lomekwi.cine.resource.media;

import com.lomekwi.cine.pipeline.Processor;
import com.lomekwi.cine.pipeline.decode.VideoDecProc;
import com.lomekwi.cine.resource.decoder.VdoDecRes;

import java.io.FileNotFoundException;

public class VdoRes extends MedRes {

    public VdoRes(String path) throws FileNotFoundException {
        super(path);
        decRes = new VdoDecRes(this);
    }
    @Override
    public VdoDecRes getDecoder() {
        return (VdoDecRes) decRes;
    }
    @Override
    public Processor getNextProcessor() {
        return VideoDecProc.getInstance();
    }
    public int getWidth(){
        return ((VdoDecRes)decRes).getWidth();
    }
    public int getHeight(){
        return ((VdoDecRes)decRes).getHeight();
    }

}
