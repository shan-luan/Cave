package com.lomekwi.cave.resource.media;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.lomekwi.cave.app.App;
import com.lomekwi.cave.pipeline.audio.AudFrame;
import com.lomekwi.cave.resource.decoder.AudDecRes;
import com.lomekwi.cave.resource.decoder.DecRes;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class AudRes extends MedRes{
    @Serial
    private static final long serialVersionUID = 1L;
    private long frameLength;

    private transient Waveformer waveformer = new Waveformer();

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

    // ---- 委托给 Waveformer ----

    public Texture getWaveTexture() {
        return waveformer.getTexture();
    }

    public int getWaveTexWidth() {
        return waveformer.texWidth;
    }

    public int getWaveTexHeight() {
        return waveformer.texHeight;
    }

    public long getBucketDuration() {
        return waveformer.bucketDuration;
    }

    public int getTotalBuckets() {
        return waveformer.totalBuckets;
    }

    /** 确保指定时间范围内的峰值已被请求生成 */
    public void ensureVisible(long startTime, long endTime) {
        waveformer.ensureVisible(startTime, endTime);
    }

    public boolean isWaveDirty() {
        return waveformer.dirty;
    }

    public void clearWaveDirty() {
        waveformer.dirty = false;
    }

    public Pixmap getWavePixmap() {
        return waveformer.pixmap;
    }

    @Serial
    private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
        ois.defaultReadObject();
        waveformer = new Waveformer();
    }

    // -----------------------------------------------------------------
    // 内部类：波形峰值纹理（按需生成）
    // -----------------------------------------------------------------

    private class Waveformer {
        static final int DECIMATED_RATE = 400;
        final long bucketDuration = 1_000_000L / DECIMATED_RATE;
        final int totalBuckets;
        final int texWidth = 512;
        final int texHeight;

        // 缓存（GL 线程访问）
        private transient Pixmap pixmap;
        private transient Texture waveTex;
        private transient boolean[] queued;
        private transient volatile boolean dirty;

        // Worker
        private final transient ConcurrentLinkedQueue<Integer> pendingSlots =
            new ConcurrentLinkedQueue<>();
        private final transient AtomicBoolean workerRunning = new AtomicBoolean(false);

        Waveformer() {
            totalBuckets = Math.max(1, (int)(duration / 1_000_000L * DECIMATED_RATE));
            texHeight = (totalBuckets + texWidth - 1) / texWidth;
            queued = new boolean[totalBuckets];

            pixmap = new Pixmap(texWidth, texHeight, Pixmap.Format.RGBA8888);
            pixmap.setColor(0, 0, 0, 1);
            pixmap.fill();

            Gdx.app.postRunnable(() -> {
                waveTex = new Texture(pixmap);
                waveTex.setFilter(
                    Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
            });
        }

        Texture getTexture() {
            return waveTex;
        }

        void ensureVisible(long startTime, long endTime) {
            int start = (int)(startTime / bucketDuration);
            int end = (int)(endTime / bucketDuration);
            if (start < 0) start = 0;
            if (end > totalBuckets) end = totalBuckets;

            boolean needWorker = false;
            for (int i = start; i < end; i++) {
                if (!queued[i]) {
                    queued[i] = true;
                    pendingSlots.offer(i);
                    needWorker = true;
                }
            }
            if (needWorker) {
                ensureWorker();
            }
        }

        private void ensureWorker() {
            if (workerRunning.compareAndSet(false, true)) {
                App.workerExecutor.submit(this::processPendingSlots);
            }
        }

        private void processPendingSlots() {
            AudDecRes dec = newDecoder();
            try {
                dec.start();
                AudFrame frame = new AudFrame(44100, 2);

                while (true) {
                    Integer idx = pendingSlots.poll();
                    if (idx == null) break;

                    if (pixmap != null) {
                        // 在 GL 线程检查是否已生成（通过 pixmap 的 R 通道判断）
                        // 简化：已经在 queued 里做过去重，直接生成
                    }

                    try {
                        long t = (long)idx * bucketDuration;
                        dec.sync(t);
                        dec.get(t, frame);
                        float[] samples = frame.getSamples();
                        if (samples != null) {
                            float max = 0;
                            for (float s : samples) {
                                float abs = s < 0 ? -s : s;
                                if (abs > max) max = abs;
                            }
                            final float peak = Math.min(max, 1f);
                            final int slot = idx;
                            Gdx.app.postRunnable(() -> {
                                int px = slot % texWidth;
                                int py = slot / texWidth;
                                pixmap.setColor(peak, 0, 0, 1);
                                pixmap.drawPixel(px, py);
                                dirty = true;
                            });
                        }
                    } catch (Exception e) {
                        // 单个 bucket 失败不影响
                    }
                }
                dec.stop();
            } catch (Exception e) {
                Gdx.app.error("AudRes", "Waveform worker failed for " + getPath(), e);
            } finally {
                try { dec.close(); } catch (Exception ignored) {}
                workerRunning.set(false);
                if (!pendingSlots.isEmpty()) {
                    ensureWorker();
                }
            }
        }
    }
}
