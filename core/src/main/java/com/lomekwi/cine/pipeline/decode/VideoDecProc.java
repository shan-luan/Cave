package com.lomekwi.cine.pipeline.decode;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.lomekwi.cine.GlobalVars;
import com.lomekwi.cine.content.Clip;
import com.lomekwi.cine.pipeline.Product;
import com.lomekwi.cine.resource.decoder.VdoDecRes;
import com.lomekwi.cine.resource.media.VdoRes;
import com.lomekwi.cine.timeline.Seg;
import com.lomekwi.cine.util.Units;

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

        if (!decoder.isInitialized()) {
            decoder.setPixelFormat(avutil.AV_PIX_FMT_RGBA);
            decoder.start();
            decoder.setBufferedProduct(new PixProd(decoder.getWidth(), decoder.getHeight()));
            decoder.setCurrentSeg(seg);
        }

        final long target = Math.min((clip.getInPoint() + offset), decoder.getLengthInTime());
        final long nextFrameTime = decoder.getTimestamp() + decoder.getLengthPerFrame();
        //当片段改变或者seek时
        if (decoder.getCurrentSeg() != seg || GlobalVars.getProject().getPlayController().getPlayhead().isSought()) {
            //当时间差大于1帧，跳转
            if (Math.abs(target - decoder.getTimestamp()) > decoder.getLengthPerFrame()) {
                System.out.println("seeking:Delta time=" + (target - decoder.getTimestamp()));
                long old = decoder.getTimestamp();
                decoder.seek(target);
                System.out.println("seek time:" + (decoder.getTimestamp() - old));
                int i = 0;
                if (decoder.getTimestamp() < target) {
                    while (decoder.getTimestamp() < target) {
                        decoder.grab();
                        i++;
                    }
                    System.out.println("I frame -> target" + i + " frame");
                } else if (decoder.getTimestamp() > target) {
                    //TODO：完成前向跳转逻辑
                    System.err.println("over jumped");
                }
                //跳转结束
            }
            decoder.setCurrentSeg(seg);
        }else if (Math.abs(target - decoder.getTimestamp()) > 3* Units.SECOND) {//当时间差大于3秒，也跳
            System.err.println("WARNING:play speed too fast/slow...");
            //FIXME:播放速度异常跳转逻辑，在烂设备上seek本身就会造成超时，然后重复试图seek
                System.out.println("seeking:Delta time=" + (target - decoder.getTimestamp()));
                long old = decoder.getTimestamp();
                decoder.seek(target);
                System.out.println("seek time:" + (decoder.getTimestamp() - old));
                int i = 0;
                if (decoder.getTimestamp() < target) {
                    while (decoder.getTimestamp() < target) {
                        decoder.grab();
                        i++;
                    }
                    System.out.println("I frame -> target" + i + " frame");
                } else if (decoder.getTimestamp() > target) {
                    //TODO：完成前向跳转逻辑
                    System.err.println("over jumped");
                    //跳转结束
                }

            }
            else if (target < nextFrameTime && decoder.getBufferedProduct().getPixels() != null) {
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
