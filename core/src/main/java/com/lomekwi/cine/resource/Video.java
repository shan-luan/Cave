package com.lomekwi.cine.resource;

import com.lomekwi.cine.pipeline.decode.VideoDecProc;

public class Video extends Media {

    public Video(String path) {
        super(path);
        decProc = new VideoDecProc(this);
    }

}
