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

public class AudRes extends MedRes implements Previewable {
    @Serial
    private static final long serialVersionUID = 1L;
    private long frameLength;

    private transient Waveformer waveformer;

    public AudRes(String path) {
        super(path);
    }

    @Override
    protected void generateMetadata(DecRes<?> metadataDecRes) {
        AudDecRes adr = (AudDecRes) metadataDecRes;
        frameLength = adr.getLengthPerFrame();
        codecName = adr.getCodecName();
        codec = adr.getCodec();
    }

    @Override
    protected AudDecRes newDecoder() {
        return new AudDecRes(this);
    }

    public long getFrameLength() {
        return frameLength;
    }

    private Waveformer getWaveformer() {
        if (waveformer == null) {
            waveformer = new Waveformer();
        }
        return waveformer;
    }

    public Waveformer waveformer() {
        return getWaveformer();
    }

    @Override
    public Texture getPreview(long time) {
        getWaveformer().queueSlot(time);
        return getWaveformer().getTexture();
    }

    @Override
    public long getPreviewInterval() {
        return getWaveformer().bucketDuration;
    }

    @Serial
    private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
        ois.defaultReadObject();
        waveformer = null;
    }

    @Override
    public void close() throws Exception {
        super.close();
        if (waveformer != null) {
            waveformer.dispose();
        }
    }

    public class Waveformer {
        static final int DECIMATED_RATE = 400;
        public final long bucketDuration = 1_000_000L / DECIMATED_RATE;
        public final int totalBuckets;
        public final int texWidth = 512;
        public final int texHeight;

        public transient Pixmap pixmap;
        public transient Texture waveTex;
        private transient boolean[] queued;
        public transient volatile boolean dirty;

        private static final int BATCH_SIZE = 64;
        private transient int[] batchSlots = new int[BATCH_SIZE];
        private transient float[] batchPeaks = new float[BATCH_SIZE];
        private transient int batchCount;

        private final transient AtomicBoolean workerRunning = new AtomicBoolean(false);
        private final transient ConcurrentLinkedQueue<Integer> pendingSlots =
            new ConcurrentLinkedQueue<>();

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

        void queueSlot(long time) {
            int idx = (int)(time / bucketDuration);
            if (idx < 0) idx = 0;
            if (idx >= totalBuckets) idx = totalBuckets - 1;
            if (!queued[idx]) {
                queued[idx] = true;
                pendingSlots.offer(idx);
                ensureWorker();
            }
        }

        private void ensureWorker() {
            if (workerRunning.compareAndSet(false, true)) {
                App.workerExecutor.submit(this::processPendingSlots);
            }
        }

        private transient AudDecRes cachedDec;

        private AudDecRes getCachedDecoder() {
            if (cachedDec == null) {
                cachedDec = newDecoder();
            }
            return cachedDec;
        }

        private void processPendingSlots() {
            AudDecRes dec = getCachedDecoder();
            try {
                if (!dec.isInitialized()) {
                    dec.start();
                }
                AudFrame frame = new AudFrame(44100, 2, null);
                int[] slots = new int[BATCH_SIZE];

                while (true) {
                    int count = 0;
                    for (int i = 0; i < BATCH_SIZE; i++) {
                        Integer idx = pendingSlots.poll();
                        if (idx == null) break;
                        slots[count++] = idx;
                    }
                    if (count == 0) break;

                    java.util.Arrays.sort(slots, 0, count);
                    dec.sync((long)slots[0] * bucketDuration);

                    for (int i = 0; i < count; i++) {
                        try {
                            long t = (long)slots[i] * bucketDuration;
                            dec.get(t, frame);
                            float[] samples = frame.getSamples();
                            if (samples != null) {
                                float max = 0;
                                for (float s : samples) {
                                    float abs = s < 0 ? -s : s;
                                    if (abs > max) max = abs;
                                }
                                batchSlots[batchCount] = slots[i];
                                batchPeaks[batchCount] = Math.min(max, 1f);
                                batchCount++;
                                if (batchCount >= BATCH_SIZE) {
                                    flushBatch();
                                }
                            }
                        } catch (Exception ignored) {
                        }
                    }
                }
                flushBatch();
            } catch (Exception e) {
                Gdx.app.error("AudRes", "Waveform worker failed for " + getPath(), e);
            } finally {
                workerRunning.set(false);
                if (!pendingSlots.isEmpty()) {
                    ensureWorker();
                }
            }
        }

        private void flushBatch() {
            if (batchCount == 0) return;
            final int n = batchCount;
            final int[] slots = java.util.Arrays.copyOf(batchSlots, n);
            final float[] peaks = java.util.Arrays.copyOf(batchPeaks, n);
            Gdx.app.postRunnable(() -> {
                for (int i = 0; i < n; i++) {
                    int px = slots[i] % texWidth;
                    int py = slots[i] / texWidth;
                    pixmap.setColor(peaks[i], 0, 0, 1);
                    pixmap.drawPixel(px, py);
                }
                dirty = true;
            });
            batchCount = 0;
        }

        void dispose() {
            if (cachedDec != null) {
                try { cachedDec.close(); } catch (Exception ignored) {}
                cachedDec = null;
            }
        }
    }
}
