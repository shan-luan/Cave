package com.lomekwi.cine.pipeline.decode;

import com.lomekwi.cine.GlobalVars;
import com.lomekwi.cine.content.Clip;
import com.lomekwi.cine.pipeline.Processor;
import com.lomekwi.cine.pipeline.Product;
import com.lomekwi.cine.resource.Video;
import com.lomekwi.cine.timeline.Segment;

import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;

import java.nio.ByteBuffer;
import java.util.Queue;

public class VideoDecoder implements Decoder<Video> {

    private final FFmpegFrameGrabber grabber;
    private final Pixels outputPixels;
    private Clip<Video> currentClip;

    private final long lengthPerFrame;
    private final long length;

    public VideoDecoder(Video video) {
        grabber = new FFmpegFrameGrabber(video.getPath());
        grabber.setPixelFormat(avutil.AV_PIX_FMT_RGBA);
        try {
            grabber.start();
            outputPixels = new Pixels(grabber.getImageWidth(), grabber.getImageHeight());
        } catch (FrameGrabber.Exception e) {
            throw new RuntimeException(e);
        }

        length = grabber.getLengthInTime();
        lengthPerFrame = length / grabber.getLengthInVideoFrames();
    }

    @Override
    public void close() throws FrameGrabber.Exception {
        grabber.stop();
        grabber.close();
        outputPixels.dispose();
    }

    @Override
    public void process(Segment<Clip<Video>> segment, Queue<Product> collector) {
        Clip<Video> clip = segment.getElement();
        long current = GlobalVars.getProject().getPlayController().getPlayhead().getTime();
        long offset = current - segment.getStart();
        long target = clip.getInPoint() + offset;

        if (target > length) {
            target = length;
        }

        long nextFrameTime = grabber.getTimestamp() + lengthPerFrame;

        try {
            Frame frame;
            //FIXME:setTimestamp实际上是设置为不大于参数时间的最后I帧的时间，现有逻辑有问题
            if(currentClip != clip || GlobalVars.getProject().getPlayController().getPlayhead().isSought()){
                if(Math.abs(target-grabber.getTimestamp())>lengthPerFrame){
                    grabber.setTimestamp(target);
                }
                currentClip =clip;
            }else if(target<nextFrameTime && outputPixels.getPixels()!=null) {
                collector.add(outputPixels);
                return;
            }
            //调用process的速率比帧率要高很多，所以不太需要考虑跳过帧。即，不执行上面的返回缓存帧就相当于跳过帧。
            //TODO:在抓取时间远小于目标时间时，靠上面的if不执行来补偿太慢（只相当于二倍速播放，在60fps调用、30fps视频下。）因此，考虑增加直接seek的逻辑
            frame = grabber.grabImage();
            if (frame != null) {
                outputPixels.setPixels((ByteBuffer) frame.image[0]);
                collector.add(outputPixels);
            }
        } catch (FrameGrabber.Exception e) {
            throw new RuntimeException(e);
        }
    }
}
