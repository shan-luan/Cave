package com.lomekwi.cine.pipeline.decode;

import com.google.common.collect.Range;
import com.lomekwi.cine.GlobalVars;
import com.lomekwi.cine.content.Clip;
import com.lomekwi.cine.pipeline.Product;
import com.lomekwi.cine.resource.decoder.VdoDecRes;
import com.lomekwi.cine.resource.media.VdoRes;

import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;
import org.jspecify.annotations.NonNull;

import java.nio.ByteBuffer;
import java.util.Queue;

//TODO:解码音频，音视频流同步
//TODO：把这一堆东西改成非阻塞的
public class VideoDecProc implements DecProc {
    private final static VideoDecProc instance = new VideoDecProc();

    public static VideoDecProc getInstance() {
        return instance;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void process(Product product, Queue<Product> collector) throws FrameGrabber.Exception {

        //TODO:把这一堆东西拆成私有方法
        final Clip<VdoRes> clip = (Clip<VdoRes>) product;
        final Range<@NonNull Long> clipRange = GlobalVars.getCurrentElementRangeIn(clip.getTrack());
        final VdoDecRes decoder = clip.getSource().getDecoder();
        final long current = GlobalVars.getProject().getPlayController().getPlayhead().getTime();
        final long offset = current - clipRange.lowerEndpoint();

        Frame output;

        if (!decoder.isInitialized()) {
            decoder.setPixelFormat(avutil.AV_PIX_FMT_RGBA);
            decoder.start();
            decoder.setBufferedProduct(new PixProd(decoder.getWidth(), decoder.getHeight()));
            decoder.setCurrentClipRange(clipRange);
        }

        final long target = Math.min((clip.getInPoint() + offset), decoder.getLengthInTime());
        final long nextFrameTime = decoder.getTimestamp() + decoder.getLengthPerFrame();
        //当片段改变或者seek时
        if (!decoder.getCurrentClipRange().equals(clipRange) || GlobalVars.getProject().getPlayController().getPlayhead().isSought()) {
            //当时间差大于1帧，跳转
            System.out.println("new seg/play head sought");
            if (Math.abs(target - decoder.getTimestamp()) > decoder.getLengthPerFrame()) {
                System.out.println("seeking:Delta time=" + (target - decoder.getTimestamp()));
                long old = decoder.getTimestamp();
                decoder.seek(target);
                System.out.println("seek time:" + (decoder.getTimestamp() - old));
                //向后跳转ffmpegFrameGrabber已经帮我们做过了
                if (decoder.getTimestamp() > target) {
                    //TODO：完成前向跳转逻辑
                    System.err.println("over jumped,Delta:"+(decoder.getTimestamp()- target));
                }
                //跳转结束
            }
            decoder.setCurrentClipRange(clipRange);
        } else if (target < nextFrameTime && decoder.getBufferedProduct().getPixels() != null) {
            collector.add(decoder.getBufferedProduct());
            return;
        }
        //调用process的速率比帧率要高很多，所以不太需要考虑跳过帧。即，不执行上面的返回缓存帧就相当于跳过帧。
        //TODO:在抓取时间远小于目标时间时，靠上面的if不执行来补偿太慢（只相当于二倍速播放，在60fps调用、30fps视频下。）因此，考虑增加直接seek的逻辑
        output = decoder.grab();
        if (output != null) {
            decoder.getBufferedProduct().setPixels((ByteBuffer) output.image[0]);
            collector.add(decoder.getBufferedProduct());
        }
    }
}
