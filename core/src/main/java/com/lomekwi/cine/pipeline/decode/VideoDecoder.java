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

    @Override
    public void process(Product product, Queue<Product> collector) {
        VideoClip videoClip = (VideoClip) product;
        long current = videoClip.getCurrentTime();
        long offset = current - videoClip.getStart();
        long target = videoClip.getInPoint() + offset;
        long length = grabber.getLengthInTime();
        if(target > length)
            target = length;
        try {
            //FIXME:每帧跳转太他妈的耗性能了，哪个傻逼想出来的主意
            //grabber.setTimestamp(target);
            Frame frame =grabber.grabImage();

            outputPixels.setPixels((ByteBuffer) frame.image[0]);
            collector.add(outputPixels);

        } catch (FrameGrabber.Exception e) {
            throw new RuntimeException(e);
        }
    }


}
