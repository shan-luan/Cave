package com.lomekwi.cine.resource;

import com.lomekwi.cine.pipeline.decode.Decoder;
import com.lomekwi.cine.pipeline.decode.VideoDecoder;

public class Video extends Media {

    public Video(String path) {
        super(path);
        decoder = new VideoDecoder(this);
    }

}
