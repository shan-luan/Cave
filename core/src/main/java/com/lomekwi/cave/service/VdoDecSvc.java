package com.lomekwi.cave.service;

import com.lomekwi.cave.pipeline.image.ImgProd;
import com.lomekwi.cave.resource.decoder.VdoDecRes;

import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.Frame;

import java.nio.ByteBuffer;

public class VdoDecSvc {
    /**
     * 解码指定时间的视频帧,输出到VdoDecRes的缓存
     * @param time 目标解码时间戳
     * @param decoder 视频解码解码器和上下文
     * @throws Exception 解码异常
     */
    public static ByteBuffer decode(long time, VdoDecRes decoder) throws Exception {
        Frame output;

        // 初始化解码器
        if (!decoder.isInitialized()) {
            decoder.setPixelFormat(avutil.AV_PIX_FMT_RGBA);
            decoder.start();
            decoder.setBufferedProduct(new ImgProd());
        }

        final long target = Math.min(time, decoder.getLengthInTime());
        final long nextFrameTime = decoder.getTimestamp() + decoder.getLengthPerFrame();
        final long diff = time-decoder.getLastGrabTime();

        // 当请求的时间更早，跳转
        if (diff<0) {
            decoder.seek(target);
            // 向后跳转ffmpegFrameGrabber已经帮我们做过了
            if (decoder.getTimestamp() > target) {
                // TODO：完成前向跳转逻辑。但实际上如果跳过头了会根据下面的逻辑自动等播放进度追上来
                System.err.println("over jumped,Delta:" + (decoder.getTimestamp() - target));
            }
        } else if ((target < nextFrameTime) && decoder.getBufferedProduct().getPixels() != null) {
            // 如果(目标时间在下一帧之前或请求的时间相同)且缓冲区有数据，则直接返回缓冲帧.
            decoder.setLastGrabTime(time);
            return decoder.getBufferedProduct().getPixels();
        }

        // 抓取新帧
        output = decoder.grab();
        if (output != null) {
            decoder.getBufferedProduct().setPixels((ByteBuffer) output.image[0]);
        }
        decoder.setLastGrabTime(time);
        return decoder.getBufferedProduct().getPixels();
    }
}
