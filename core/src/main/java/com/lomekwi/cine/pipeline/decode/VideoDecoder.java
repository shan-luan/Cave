package com.lomekwi.cine.pipeline.decode;

import com.badlogic.gdx.graphics.Pixmap;
import com.lomekwi.cine.content.VideoClip;
import com.lomekwi.cine.pipeline.Processor;
import com.lomekwi.cine.pipeline.Product;
import com.lomekwi.cine.pipeline.render.PixmapProduct;
import com.lomekwi.cine.resource.Video;

import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.CanvasFrame;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;

import java.nio.ByteBuffer;
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
        long current = videoClip.getCurrentTime();
        long offset = current - videoClip.getStart();
        long target = videoClip.getInPoint() + offset;
        long length = grabber.getLengthInTime();
        if(target > length)
            target = length;
        try {
            grabber.setTimestamp(target);
            Frame frame =grabber.grabImage();
            collector.add(convert(frame,new PixmapProduct(frame.imageWidth, frame.imageHeight, Pixmap.Format.RGBA8888, videoClip)));
        } catch (FrameGrabber.Exception e) {
            throw new RuntimeException(e);
        }
    }

    private PixmapProduct convert(Frame frame, PixmapProduct pixmap) {
        if (frame == null || frame.image[0] == null) return null;
        ByteBuffer frameBuffer = ((ByteBuffer) frame.image[0]).duplicate();
        ByteBuffer pixmapBuffer = pixmap.getPixels();
        pixmapBuffer.position(0);
        frameBuffer.rewind();
        pixmapBuffer.put(frameBuffer);
        pixmapBuffer.flip();
        return pixmap;
    }

}
