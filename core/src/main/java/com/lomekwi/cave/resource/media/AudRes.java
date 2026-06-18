package com.lomekwi.cave.resource.media;

import com.badlogic.gdx.Gdx;
import com.lomekwi.cave.app.App;
import com.lomekwi.cave.pipeline.audio.AudFrame;
import com.lomekwi.cave.resource.decoder.AudDecRes;
import com.lomekwi.cave.resource.decoder.DecRes;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serial;

public class AudRes extends MedRes{
    @Serial
    private static final long serialVersionUID = 1L;
    private long frameLength;

    // ---- waveform ----
    public static final int PEAKS_PER_SECOND = 100;
    private transient volatile boolean peaksReady;
    private transient volatile boolean peaksGenerating;
    private transient float[] peaks;

    /**
     * 必须确保路径对应一个存在的文件
     */
    public AudRes(String path) {
        super(path);
    }

    @Override
    protected void generateMetadata(DecRes<?> metadataDecRes) {
        frameLength= metadataDecRes.getLengthPerFrame();

    }

    @Override
    protected AudDecRes newDecoder() {
        return new AudDecRes(this);
    }

    public long getFrameLength() {
        return frameLength;
    }

    // ---- waveform ----

    /**
     * 非阻塞：数据就绪则返回数组，否则触发后台生成并返回 null。
     * 调用方应检查 null 并绘制降级占位。
     */
    public float[] getPeaks() {
        if (!peaksReady && !peaksGenerating) {
            peaksGenerating = true;
            App.workerExecutor.submit(this::decodePeaks);
        }
        return peaksReady ? peaks : null;
    }

    private void decodePeaks() {
        if (duration <= 0) {
            Gdx.app.postRunnable(() -> peaksGenerating = false);
            return;
        }

        int totalPeaks = Math.max(1, (int)(duration / 1_000_000L * PEAKS_PER_SECOND));
        float[] result = new float[totalPeaks];
        long bucketDuration = 1_000_000L / PEAKS_PER_SECOND;

        AudDecRes dec = newDecoder();
        try {
            dec.start();
            AudFrame frame = new AudFrame(44100, 2);

            for (int i = 0; i < totalPeaks; i++) {
                long time = i * bucketDuration;
                dec.sync(time);
                dec.get(time, frame);
                float[] samples = frame.getSamples();
                if (samples != null) {
                    float max = 0;
                    for (float s : samples) {
                        float abs = s < 0 ? -s : s;
                        if (abs > max) max = abs;
                    }
                    result[i] = Math.min(max, 1f);
                }
            }
            dec.stop();
        } catch (Exception e) {
            Gdx.app.error("AudRes", "Failed to decode waveform for " + getPath(), e);
            Gdx.app.postRunnable(() -> peaksGenerating = false);
            return;
        } finally {
            try { dec.close(); } catch (Exception ignored) {}
        }

        Gdx.app.postRunnable(() -> {
            peaks = result;
            peaksReady = true;
        });
    }

    @Serial
    private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
        ois.defaultReadObject();
        peaks = null;
        peaksReady = false;
        peaksGenerating = false;
    }
}
