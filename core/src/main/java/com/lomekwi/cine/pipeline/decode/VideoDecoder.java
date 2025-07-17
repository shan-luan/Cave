package com.lomekwi.cine.pipeline.decode;

import com.lomekwi.cine.content.VideoClip;
import com.lomekwi.cine.pipeline.Processor;
import com.lomekwi.cine.pipeline.Product;
import com.lomekwi.cine.resource.Video;

import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;

import java.nio.ByteBuffer;
import java.util.Queue;

public class VideoDecoder implements Processor {

    private final FFmpegFrameGrabber grabber;
    private final Pixels outputPixels;
    private VideoClip clip;

    public VideoDecoder(Video video) {
        grabber = new FFmpegFrameGrabber(video.getPath());
        grabber.setPixelFormat(avutil.AV_PIX_FMT_RGBA);
        try {
            grabber.start();
            outputPixels = new Pixels(grabber.getImageWidth(), grabber.getImageHeight());
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
        outputPixels.dispose();
    }
    //TODO:丢帧逻辑，当前调用频率高于帧率或低于帧率会出现不准确
    @Override
    public void process(Product product, Queue<Product> collector) {
        VideoClip videoClip = (VideoClip) product;
        long current = videoClip.getPlayhead().getTime();
        long offset = current - videoClip.getStart();
        long target = videoClip.getInPoint() + offset;

        if (target > grabber.getLengthInTime()) {
            target = grabber.getLengthInTime();
        }

        try {
            if(clip!=videoClip || videoClip.getPlayhead().isSought()){
                if(Math.abs(target-grabber.getTimestamp())>1000){
                    grabber.setTimestamp(target);
                }
                clip = videoClip;
            }
            Frame frame = grabber.grabImage();
            if (frame != null) {
                outputPixels.setPixels((ByteBuffer) frame.image[0]);
                collector.add(outputPixels);
            }
        } catch (FrameGrabber.Exception e) {
            throw new RuntimeException(e);
        }
    }
}
