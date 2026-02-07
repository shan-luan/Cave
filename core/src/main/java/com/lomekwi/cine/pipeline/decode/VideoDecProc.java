package com.lomekwi.cine.pipeline.decode;

import com.lomekwi.cine.GlobalVars;
import com.lomekwi.cine.content.Clip;
import com.lomekwi.cine.pipeline.Product;
import com.lomekwi.cine.resource.decoder.VdoDecRes;
import com.lomekwi.cine.resource.media.VdoRes;
import com.lomekwi.cine.timeline.Seg;

import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;

import java.nio.ByteBuffer;
import java.util.Queue;
//TODO:解码音频，音视频流同步
//TODO：把这一堆东西改成非阻塞的
public class VideoDecProc implements DecProc {
    private final static VideoDecProc instance=new VideoDecProc();

    public static VideoDecProc getInstance(){
        return instance;
    }
    @Override
    @SuppressWarnings("unchecked")
    public void process(Product product, Queue<Product> collector) throws FrameGrabber.Exception {

        //TODO:把这一堆东西拆成私有方法

        final Seg seg = (Seg) product;
        final Clip<VdoRes> clip = (Clip<VdoRes>) seg.getElement();
        final VdoDecRes decoder = clip.getSource().getDecoder();
        final long current = GlobalVars.getProject().getPlayController().getPlayhead().getTime();
        final long offset = current - seg.getStart();

        Frame output;

        if(!decoder.isInitialized()){
            decoder.setPixelFormat(avutil.AV_PIX_FMT_RGBA);
            decoder.start();
            decoder.setBufferedProduct(new PixProd(decoder.getWidth(), decoder.getHeight()));
            decoder.setCurrentSeg(seg);
        }

        final long target = Math.min((clip.getInPoint() + offset), decoder.getLengthInTime());
        final long nextFrameTime = decoder.getTimestamp() + decoder.getLengthPerFrame();

        if(decoder.getCurrentSeg() != seg || GlobalVars.getProject().getPlayController().getPlayhead().isSought()){
            if(Math.abs(target-decoder.getTimestamp())>decoder.getLengthPerFrame()){
                decoder.seek(target);
                while (decoder.getTimestamp()<target){
                    decoder.grab();
                }
            }
            decoder.setCurrentSeg(seg);
        } else if(target<nextFrameTime && decoder.getBufferedProduct().getPixels()!=null) {
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
