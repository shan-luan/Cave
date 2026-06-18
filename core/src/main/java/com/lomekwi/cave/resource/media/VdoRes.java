package com.lomekwi.cave.resource.media;

import static com.lomekwi.cave.util.Units.SECOND;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.lomekwi.cave.app.App;
import com.lomekwi.cave.resource.decoder.DecRes;
import com.lomekwi.cave.resource.decoder.VdoDecRes;
import org.bytedeco.javacv.Frame;

import com.badlogic.gdx.utils.IntMap;

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

    // ---- thumbnail ----
    private static final int THUMB_HEIGHT = 80;
    private final transient long thumbInterval = SECOND;
    private transient int slotCount;
    private transient IntMap<Texture> thumbnails; // GL 线程独占
    private transient boolean[] queued;           // GL 线程独占，防重复入队
    private final transient ConcurrentLinkedQueue<Integer> pendingSlots =
        new ConcurrentLinkedQueue<>();
    private final transient AtomicBoolean workerRunning = new AtomicBoolean(false);

    private void initCache() {
        slotCount = (int)(duration / thumbInterval) + 1;
        thumbnails = new IntMap<>();
        queued = new boolean[slotCount];
    }

    public VdoRes(String path){
        super(path);
        initCache();
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

    // ---- thumbnail ----

    /**
     * 非阻塞：返回 srcTime 处或之前最近的缩略图。未命中时触发后台生成。
     */
    public Texture getThumbnail(long srcTime) {
        int idx = (int)(srcTime / thumbInterval);
        if (idx < 0) idx = 0;
        if (idx >= slotCount) idx = slotCount - 1;

        // 按需触发生成（同一 slot 只入队一次）
        if (!thumbnails.containsKey(idx) && !queued[idx]) {
            queued[idx] = true;
            pendingSlots.offer(idx);
            ensureWorker();
        }

        // 向前扫描 floor entry
        for (int i = idx; i >= 0; i--) {
            Texture t = thumbnails.get(i);
            if (t != null) return t;
        }
        return null;
    }

    public long getThumbInterval() {
        return thumbInterval;
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
                    long t = (long)idx * thumbInterval;
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
                            if (!thumbnails.containsKey(slot)) {
                                thumbnails.put(slot, new Texture(small));
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

    @Override
    public void close() throws Exception {
        super.close();
        if (thumbnails != null) {
            for (Texture t : thumbnails.values()) {
                t.dispose();
            }
        }
    }

    @Serial
    private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
        ois.defaultReadObject();
        initCache();
    }
}
