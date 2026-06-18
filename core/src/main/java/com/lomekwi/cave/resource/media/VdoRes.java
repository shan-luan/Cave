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

    // -----------------------------------------------------------------
    // 内部类：缩略图缓存与按需生成
    // -----------------------------------------------------------------

    private class Thumbnailer {
        private static final int THUMB_HEIGHT = 80;
        final long interval = SECOND;
        final int slotCount;
        private final IntMap<Texture> cache = new IntMap<>();
        private final boolean[] queued;
        private final ConcurrentLinkedQueue<Integer> pendingSlots =
            new ConcurrentLinkedQueue<>();
        private final AtomicBoolean workerRunning = new AtomicBoolean(false);

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

        private void processPendingSlots() {
            VdoDecRes dec = newDecoder();
            try {
                dec.start();
                float aspect = (float) width / height;
                int thumbW = Math.max(1, (int)(THUMB_HEIGHT * aspect));

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
                            Pixmap full = new Pixmap(width, height, Pixmap.Format.RGBA8888);
                            full.getPixels().put(buf);

                            Pixmap small = new Pixmap(thumbW, THUMB_HEIGHT, Pixmap.Format.RGBA8888);
                            small.drawPixmap(full, 0, 0, width, height, 0, 0, thumbW, THUMB_HEIGHT);
                            full.dispose();

                            final int slot = idx;
                            Gdx.app.postRunnable(() -> {
                                if (!cache.containsKey(slot)) {
                                    cache.put(slot, new Texture(small));
                                }
                                small.dispose();
                            });
                        }
                    } catch (Exception e) {
                        // 单个 slot 失败不影响其他
                    }
                }
                dec.stop();
            } catch (Exception e) {
                Gdx.app.error("VdoRes", "Thumbnail worker failed for " + getPath(), e);
            } finally {
                try { dec.close(); } catch (Exception ignored) {}
                workerRunning.set(false);
                if (!pendingSlots.isEmpty()) {
                    ensureWorker();
                }
            }
        }

        void dispose() {
            for (Texture t : cache.values()) {
                t.dispose();
            }
        }
    }
}
