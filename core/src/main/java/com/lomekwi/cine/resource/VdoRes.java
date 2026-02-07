package com.lomekwi.cine.resource;

import com.lomekwi.cine.pipeline.decode.VideoDecProc;

public class VdoRes extends MedRes {

    public VdoRes(String path) {
        super(path);
        decProc = new VideoDecProc(this);
    }

}
