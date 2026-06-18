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

    private transient Waveformer waveformer = new Waveformer();

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

    public float[] getPeaks() {
        return waveformer.getPeaks();
    }

    public long getBucketDuration() {
        return waveformer.bucketDuration;
    }

    @Serial
    private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
        ois.defaultReadObject();
        waveformer = new Waveformer();
    }

    // -----------------------------------------------------------------
    // 内部类：波形数据
    // -----------------------------------------------------------------

    private class Waveformer {
        static final int PEAKS_PER_SECOND = 100;
        final long bucketDuration = 1_000_000L / PEAKS_PER_SECOND;
        private transient volatile boolean ready;
        private transient volatile boolean generating;
        private transient float[] peaks;

        float[] getPeaks() {
            if (!ready && !generating) {
                generating = true;
                App.workerExecutor.submit(this::decode);
            }
            return ready ? peaks : null;
        }

        private void decode() {
            if (duration <= 0) {
                Gdx.app.postRunnable(() -> generating = false);
                return;
            }

            int total = Math.max(1, (int)(duration / 1_000_000L * PEAKS_PER_SECOND));
            float[] result = new float[total];

            AudDecRes dec = newDecoder();
            try {
                dec.start();
                AudFrame frame = new AudFrame(44100, 2);

                for (int i = 0; i < total; i++) {
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
                Gdx.app.error("AudRes", "Waveform decode failed for " + getPath(), e);
                Gdx.app.postRunnable(() -> generating = false);
                return;
            } finally {
                try { dec.close(); } catch (Exception ignored) {}
            }

            Gdx.app.postRunnable(() -> {
                peaks = result;
                ready = true;
            });
        }
    }
}
