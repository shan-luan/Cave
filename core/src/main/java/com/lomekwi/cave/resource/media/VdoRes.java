package com.lomekwi.cave.resource.media;

import static com.lomekwi.cave.util.Units.SECOND;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.lomekwi.cave.app.App;
import com.lomekwi.cave.resource.decoder.DecRes;
import com.lomekwi.cave.resource.decoder.VdoDecRes;
import org.bytedeco.javacv.Frame;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListMap;
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
    private transient ConcurrentSkipListMap<Long, Texture> thumbnailCache =
        new ConcurrentSkipListMap<>();
    private final transient ConcurrentLinkedQueue<Long> pendingSlots =
        new ConcurrentLinkedQueue<>();
    private final transient AtomicBoolean workerRunning = new AtomicBoolean(false);

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

    // ---- thumbnail ----

    /**
     * 非阻塞：若 srcTime 对应的 slot 未缓存，加入待生成队列。
     * 始终返回 floor entry（最接近的前一个缩略图），缓存为空时返回 null。
     */
    public Texture getThumbnail(long srcTime) {
        long slot = (srcTime / thumbInterval) * thumbInterval;

        if (!thumbnailCache.containsKey(slot)) {
            pendingSlots.offer(slot);
            ensureWorker();
        }

        Map.Entry<Long, Texture> entry = thumbnailCache.floorEntry(slot);
        return entry != null ? entry.getValue() : null;
    }

    public long getThumbInterval() {
        return thumbInterval;
    }

    private void ensureWorker() {
        if (workerRunning.compareAndSet(false, true)) {
            App.workerExecutor.submit(this::processPendingSlots);
        }
    }

    /**
     * Worker 线程：循环从队列取 slot，seek 解码，post 纹理到 GL 线程写入缓存。
     * 队列空则退出，退出前检查是否有新请求进来。
     */
    private void processPendingSlots() {
        VdoDecRes dec = newDecoder();
        try {
            dec.start();
            float aspect = (float) width / height;
            int thumbW = Math.max(1, (int)(THUMB_HEIGHT * aspect));

            while (true) {
                Long slot = pendingSlots.poll();
                if (slot == null) break;

                // 可能已被之前的请求处理过
                if (thumbnailCache.containsKey(slot)) continue;

                try {
                    long t = slot;
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

                        final long srcTime = t;
                        Gdx.app.postRunnable(() -> {
                            if (!thumbnailCache.containsKey(srcTime)) {
                                thumbnailCache.put(srcTime, new Texture(small));
                            }
                            small.dispose();
                        });
                    }
                } catch (Exception e) {
                    // 单个 slot 失败不影响其他，跳过继续
                }
            }
            dec.stop();
        } catch (Exception e) {
            Gdx.app.error("VdoRes", "Thumbnail worker failed for " + getPath(), e);
        } finally {
            try { dec.close(); } catch (Exception ignored) {}
            workerRunning.set(false);
            // 处理期间新来的请求
            if (!pendingSlots.isEmpty()) {
                ensureWorker();
            }
        }
    }

    @Serial
    private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
        ois.defaultReadObject();
        thumbnailCache = new ConcurrentSkipListMap<>();
        // pendingSlots / workerRunning 保持默认即可
    }
}
