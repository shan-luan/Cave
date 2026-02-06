package com.lomekwi.cine.pipeline.decode;

import com.lomekwi.cine.GlobalVars;
import com.lomekwi.cine.content.Clip;
import com.lomekwi.cine.pipeline.Product;
import com.lomekwi.cine.resource.Video;
import com.lomekwi.cine.timeline.Segment;

import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;

import java.nio.ByteBuffer;
import java.util.Queue;
//TODO:解码音频，音视频流同步
//TODO:把“解码器”这一资源和无状态的解码逻辑(应该放在一个单例里)拆分
public class VideoDecoder implements Decoder<Video> {

    private final FFmpegFrameGrabber grabber;
    private final Pixels outputPixels;
    private Segment<Clip<Video>> currentSegment;

    private final long lengthPerFrame;
    private final long length;

    private int j=0;

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
    //TODO:把这一堆东西拆成私有方法
    public void process(Segment<Clip<Video>> segment, Queue<Product> collector) {
        long current = GlobalVars.getProject().getPlayController().getPlayhead().getTime();
        long offset = current - segment.getStart();
        long target = segment.getElement().getInPoint() + offset;

        if (target > length) {
            target = length;
        }

        long nextFrameTime = grabber.getTimestamp() + lengthPerFrame;

        try {
            Frame frame;
            if(currentSegment != segment || GlobalVars.getProject().getPlayController().getPlayhead().isSought()){
                if(Math.abs(target-grabber.getTimestamp())>lengthPerFrame){
                    grabber.setTimestamp(target);
                    System.out.println((Math.abs(target-grabber.getTimestamp())-lengthPerFrame)/(double)lengthPerFrame+"(sought target:now target delta frame)");
                    int i=0;
                    while (grabber.getTimestamp()<target){
                        grabber.grabImage();
                        i++;
                        System.out.println("sought"+ i+"frame");
                    }
                }
                currentSegment = segment;
            }else if(target<nextFrameTime && outputPixels.getPixels()!=null) {
                j++;
                System.out.println("skipped"+j+"frame");
                collector.add(outputPixels);
                return;
            }
            //调用process的速率比帧率要高很多，所以不太需要考虑跳过帧。即，不执行上面的返回缓存帧就相当于跳过帧。
            //TODO:在抓取时间远小于目标时间时，靠上面的if不执行来补偿太慢（只相当于二倍速播放，在60fps调用、30fps视频下。）因此，考虑增加直接seek的逻辑
            frame = grabber.grabImage();
            j=0;
            if (frame != null) {
                outputPixels.setPixels((ByteBuffer) frame.image[0]);
                collector.add(outputPixels);
            }
        } catch (FrameGrabber.Exception e) {
            throw new RuntimeException(e);
        }
    }
}
