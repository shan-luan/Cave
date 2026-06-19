package com.lomekwi.cave.resource.media;

import static com.lomekwi.cave.util.Units.SECOND;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.IntMap;
import com.lomekwi.cave.app.App;
import com.lomekwi.cave.resource.decoder.DecRes;
import com.lomekwi.cave.resource.decoder.VdoDecRes;
import org.bytedeco.javacv.Frame;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class VdoRes extends MedRes {
    private int width;
    private int height;
    private long frameLength;
    @Serial
    private static final long serialVersionUID = 1L;

    private transient Thumbnailer thumbnailer = new Thumbnailer();

    public VdoRes(String path){
        super(path);
    }
    @Override
    public VdoDecRes getDecoder(int trackIndex) {
        return (VdoDecRes) super.getDecoder(trackIndex);
    }
    public int getWidth(){
        return width;
    }
    public int getHeight(){
        return height;
    }
    @Override
    protected void generateMetadata(DecRes<?> metadataDecRes) {
        VdoDecRes vdr = (VdoDecRes) metadataDecRes;
        width = vdr.getWidth();
        height = vdr.getHeight();
        frameLength = vdr.getLengthPerFrame();
    }

    public long getFrameLength() {
        return frameLength;
    }

    @Override
    protected VdoDecRes newDecoder() {
        return new VdoDecRes(this);
    }

    public Texture getThumbnail(long srcTime) {
        return thumbnailer.get(srcTime);
    }

    public long getThumbInterval() {
        return thumbnailer.interval;
    }

    @Override
    public void close() throws Exception {
        super.close();
        thumbnailer.dispose();
    }

    @Serial
    private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
        ois.defaultReadObject();
        thumbnailer = new Thumbnailer();
    }

    private class Thumbnailer {
        private static final int THUMB_HEIGHT = 80;
        private static final int BATCH_SIZE = 16;
        final long interval = SECOND;
        final int slotCount;
        private final IntMap<Texture> cache = new IntMap<>();
        private final boolean[] queued;
        private final AtomicBoolean workerRunning = new AtomicBoolean(false);
        private final ConcurrentLinkedQueue<Integer> pendingSlots =
            new ConcurrentLinkedQueue<>();

        private transient Pixmap fullPixmap;
        private transient int thumbW;

        private final int[] batchSlots = new int[BATCH_SIZE];
        private final Pixmap[] batchPixmaps = new Pixmap[BATCH_SIZE];
        private int batchCount;

        Thumbnailer() {
            slotCount = (int)(duration / interval) + 1;
            queued = new boolean[slotCount];
        }

        Texture get(long srcTime) {
            int idx = (int)(srcTime / interval);
            if (idx < 0) idx = 0;
            if (idx >= slotCount) idx = slotCount - 1;

            if (!cache.containsKey(idx) && !queued[idx]) {
                queued[idx] = true;
                pendingSlots.offer(idx);
                ensureWorker();
            }

            for (int i = idx; i >= 0; i--) {
                Texture t = cache.get(i);
                if (t != null) return t;
            }
            return null;
        }

        private void ensureWorker() {
            if (workerRunning.compareAndSet(false, true)) {
                App.workerExecutor.submit(this::processPendingSlots);
            }
        }

        private transient VdoDecRes cachedDec;

        private VdoDecRes getCachedDecoder() {
            if (cachedDec == null) {
                cachedDec = newDecoder();
            }
            return cachedDec;
        }

        private void processPendingSlots() {
            VdoDecRes dec = getCachedDecoder();
            try {
                if (!dec.isInitialized()) {
                    dec.start();
                }
                if (fullPixmap == null) {
                    fullPixmap = new Pixmap(width, height, Pixmap.Format.RGBA8888);
                    float aspect = (float) width / height;
                    thumbW = Math.max(1, (int)(THUMB_HEIGHT * aspect));
                }

                while (true) {
                    Integer idx = pendingSlots.poll();
                    if (idx == null) break;

                    try {
                        long t = (long)idx * interval;
                        dec.sync(t);
                        Frame f = dec.grab();
                        if (f != null && f.image != null && f.image[0] instanceof ByteBuffer) {
                            ByteBuffer buf = (ByteBuffer) f.image[0];
                            buf.rewind();
                            fullPixmap.getPixels().clear();
                            fullPixmap.getPixels().put(buf);

                            Pixmap small = new Pixmap(thumbW, THUMB_HEIGHT,
                                Pixmap.Format.RGBA8888);
                            small.drawPixmap(fullPixmap,
                                0, 0, width, height, 0, 0, thumbW, THUMB_HEIGHT);

                            batchSlots[batchCount] = idx;
                            batchPixmaps[batchCount] = small;
                            batchCount++;
                            if (batchCount >= BATCH_SIZE) {
                                flushBatch();
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }
                flushBatch();
            } catch (Exception e) {
                Gdx.app.error("VdoRes", "Thumbnail worker failed for " + getPath(), e);
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
            final Pixmap[] pixmaps = java.util.Arrays.copyOf(batchPixmaps, n);
            Gdx.app.postRunnable(() -> {
                for (int i = 0; i < n; i++) {
                    if (!cache.containsKey(slots[i])) {
                        cache.put(slots[i], new Texture(pixmaps[i]));
                    }
                    pixmaps[i].dispose();
                }
            });
            batchCount = 0;
        }

        void dispose() {
            for (Texture t : cache.values()) {
                t.dispose();
            }
        }
    }
}
