package com.lomekwi.cine.pipeline.decode;

import com.lomekwi.cine.content.VideoClip;
import com.lomekwi.cine.pipeline.Processor;
import com.lomekwi.cine.pipeline.Product;
import com.lomekwi.cine.resource.Video;

import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FrameGrabber;

import java.util.Queue;

public class VideoDecoder implements Processor {

    private final FFmpegFrameGrabber grabber;

    public VideoDecoder(Video video) {
        grabber = new FFmpegFrameGrabber(video.getPath());
        grabber.setPixelFormat(avutil.AV_PIX_FMT_RGBA);
        try {
            grabber.start();
        } catch (FrameGrabber.Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void dispose() {
        try {
            grabber.close();
        } catch (FrameGrabber.Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void process(Product product, Queue<Product> collector) {
        VideoClip videoClip = (VideoClip) product;
        //TODO:finish it
    }

}
