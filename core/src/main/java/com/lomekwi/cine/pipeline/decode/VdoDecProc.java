package com.lomekwi.cine.pipeline.decode;

import com.google.common.collect.Range;
import com.lomekwi.cine.GlobalVars;
import com.lomekwi.cine.content.Clip;
import com.lomekwi.cine.pipeline.Product;
import com.lomekwi.cine.resource.decoder.VdoDecRes;
import com.lomekwi.cine.resource.media.VdoRes;
import com.lomekwi.cine.service.VdoDecSvc;

import org.bytedeco.javacv.FrameGrabber;
import org.jspecify.annotations.NonNull;

import java.util.Queue;

//TODO:解码音频，音视频流同步
//TODO：把这一堆东西改成非阻塞的
public class VdoDecProc implements DecProc {
    private final static VdoDecProc instance = new VdoDecProc();

    public static VdoDecProc getInstance() {
        return instance;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void process(Product product, Queue<Product> collector) throws FrameGrabber.Exception {
        final Clip<VdoRes> clip = (Clip<VdoRes>) product;
        final Range<@NonNull Long> clipRange = GlobalVars.getCurrentElementRangeIn(clip.getTrack());
        final VdoDecRes decoder = clip.getSource().getDecoder();
        final long current = GlobalVars.getProject().getPlayController().getPlayhead().getTime();
        final long offset = current - clipRange.lowerEndpoint();

        // 计算要解码的目标时间
        final long targetTime = Math.min((clip.getOffset() + offset), decoder.getLengthInTime());

        // 复用解码器的缓冲产品
        PixProd pixProd = decoder.getBufferedProduct();
        if (pixProd == null) {
            pixProd = new PixProd(decoder.getWidth(), decoder.getHeight());
            decoder.setBufferedProduct(pixProd);
        }

        // 调用解码服务进行实际解码
        try {
            VdoDecSvc.decode(targetTime, decoder, clipRange);
        } catch (Exception e) {
            throw new FrameGrabber.Exception("Video decoding failed", e);
        }

        // 将解码结果添加到收集器
        collector.add(pixProd);
    }
}
