package com.lomekwi.cave.resource.decoder;

import com.lomekwi.cave.pipeline.image.ImgFrame;
import com.lomekwi.cave.resource.media.ImgRes;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;

import java.nio.ByteBuffer;

import static org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_RGBA;

public class ImgDecRes extends DecRes<ImgFrame> {
    private ByteBuffer cachedPixels;
    private int width;
    private int height;
    private int unpackRowLength;

    public ImgDecRes(ImgRes source) {
        super(source);
    }

    @Override
    public Frame grab() throws FFmpegFrameGrabber.Exception {
        if (!initialized) throw new IllegalStateException("Not initialized");
        return grabber.grabImage();
    }

    @Override
    public void start() throws FrameGrabber.Exception {
        grabber.setPixelFormat(AV_PIX_FMT_RGBA);
        grabber.setAudioChannels(0);
        super.start();
        width = grabber.getImageWidth();
        height = grabber.getImageHeight();
    }

    public int getWidth() {
        if (!initialized) throw new IllegalStateException("Not initialized");
        return width;
    }

    public int getHeight() {
        if (!initialized) throw new IllegalStateException("Not initialized");
        return height;
    }

    @Override
    public long getLengthPerFrame() {
        return 0;
    }

    @Override
    public void sync(long time) throws Exception {
        if (!initialized) {
            start();
        }
    }

    @Override
    public void get(long time, ImgFrame frame) throws Exception {
        if (!initialized) {
            start();
        }
        if (cachedPixels != null) {
            frame.setPixels(cachedPixels);
            return;
        }
        Frame grabbed = grab();
        if (grabbed != null && grabbed.image != null && grabbed.image[0] != null) {
            if (grabbed.imageStride > 0 && grabbed.imageChannels > 0) {
                unpackRowLength = grabbed.imageStride / grabbed.imageChannels;
            }
            ByteBuffer src = (ByteBuffer) grabbed.image[0];
            src.rewind();
            ByteBuffer copy = ByteBuffer.allocateDirect(src.limit());
            copy.put(src);
            copy.flip();
            cachedPixels = copy;
        }
        frame.setPixels(cachedPixels);
    }

    @Override
    public void seek(long time) {
    }

    public ByteBuffer getCachedPixels() {
        return cachedPixels;
    }

    public int getUnpackRowLength() {
        return unpackRowLength;
    }
}
