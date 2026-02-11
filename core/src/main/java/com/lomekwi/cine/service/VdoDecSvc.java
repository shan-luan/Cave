package com.lomekwi.cine.service;

import com.google.common.collect.Range;
import com.lomekwi.cine.pipeline.decode.PixProd;
import com.lomekwi.cine.resource.decoder.VdoDecRes;

import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.Frame;
import org.jspecify.annotations.NonNull;

import java.nio.ByteBuffer;

public class VdoDecSvc {
    private static final Range<@NonNull Long> ALL_TIME = Range.all();//防止每次查询都要新建对象，整体依然无状态
    /**
     * 解码指定时间的视频帧,输出到VdoDecRes的缓存
     * @param time 目标解码时间戳
     * @param decoder 视频解码解码器和上下文
     * @param clipRange 当前片段的时间范围
     * @throws Exception 解码异常
     */
    public static void decode(long time, VdoDecRes decoder, Range<@NonNull Long> clipRange) throws Exception {
        Frame output;

        // 初始化解码器
        if (!decoder.isInitialized()) {
            decoder.setPixelFormat(avutil.AV_PIX_FMT_RGBA);
            decoder.start();
            decoder.setBufferedProduct(new PixProd(decoder.getWidth(), decoder.getHeight()));
            decoder.setCurrentClipRange(clipRange);
        }

        final long target = Math.min(time, decoder.getLengthInTime());
        final long nextFrameTime = decoder.getTimestamp() + decoder.getLengthPerFrame();

        // 当片段改变或者seek时
        if (!decoder.getCurrentClipRange().equals(clipRange)) {
            // 当时间差大于1帧，跳转
            if (Math.abs(target - decoder.getTimestamp()) > decoder.getLengthPerFrame()) {
                long old = decoder.getTimestamp();
                decoder.seek(target);
                // 向后跳转ffmpegFrameGrabber已经帮我们做过了
                if (decoder.getTimestamp() > target) {
                    // TODO：完成前向跳转逻辑。但实际上如果跳过头了会根据下面的逻辑自动等播放进度追上来
                    System.err.println("over jumped,Delta:" + (decoder.getTimestamp() - target));
                }
            }
            decoder.setCurrentClipRange(clipRange);
        } else if (target < nextFrameTime && decoder.getBufferedProduct().getPixels() != null) {
            // 如果目标时间在下一帧之前且缓冲区有数据，则直接返回缓冲帧
            return;
        }

        // 抓取新帧
        output = decoder.grab();
        if (output != null) {
            decoder.getBufferedProduct().setPixels((ByteBuffer) output.image[0]);
        }
    }
    public static void decode(long time, VdoDecRes decoder) throws Exception {
        decode(time, decoder, ALL_TIME);
    }
}
