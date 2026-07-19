package com.lomekwi.cave.resource.media;

import com.lomekwi.cave.pipeline.Frame;
import com.lomekwi.cave.resource.Resource;
import com.lomekwi.cave.resource.decoder.DecRes;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalNotification;

import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.io.Serializable;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 *媒体资源类，指代一个在磁盘中存在，占有编解码器的资源
 */
public abstract class MedRes implements Resource, Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private final String path;
    protected long duration;
    protected @Nullable String codecName;
    protected int codec;
    private static final int DECODER_TIMEOUT_SECONDS = 30;

    private static final Set<MedRes> instances = ConcurrentHashMap.newKeySet();
    private static final ScheduledExecutorService CLEANUP = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "decoder-cache-cleanup");
        t.setDaemon(true);
        return t;
    });

    static {
        CLEANUP.scheduleWithFixedDelay(() -> {
            for (MedRes res : instances) {
                res.decoderCache.cleanUp();
            }
        }, DECODER_TIMEOUT_SECONDS, 15, TimeUnit.SECONDS);
    }

    private transient Cache<Integer, DecRes<?>> decoderCache = CacheBuilder.newBuilder()
        .expireAfterAccess(DECODER_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .removalListener((RemovalNotification<Integer, DecRes<?>> notification) -> {
            DecRes<?> dec = notification.getValue();
            if (dec != null) {
                try { dec.close(); } catch (Exception ignored) {}
            }
        })
        .build();

    /**
     * 必须确保路径对应一个存在的文件
     */
    public MedRes(String path) {
        instances.add(this);
        this.path = path;
        try (var metadataDecRes = newDecoder()) {
            metadataDecRes.start();
            generateMetadata(metadataDecRes);
            this.duration = Math.max(0, metadataDecRes.getLengthInTime());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public long getDuration() {
        return duration;
    }

    public String getPath() {
        return path;
    }

    @Nullable
    public String getCodecName() {
        return codecName;
    }

    public int getCodec() {
        return codec;
    }
    public DecRes<?> getDecoder(int trackIndex){
        try {
            return decoderCache.get(trackIndex, () -> newDecoder());
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    public void get(int trackIndex, long time, Frame frame) throws Exception {
        ((DecRes<Frame>) getDecoder(trackIndex)).get(time, frame);
    }
    public void sync(int trackIndex, long time) throws Exception {
        getDecoder(trackIndex).sync(time);
    }

    @Override
    public void close() throws Exception {
        instances.remove(this);
        decoderCache.invalidateAll();
    }

    @Serial
    private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
        ois.defaultReadObject();
        instances.add(this);
        decoderCache = CacheBuilder.newBuilder()
            .expireAfterAccess(DECODER_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .removalListener((RemovalNotification<Integer, DecRes<?>> notification) -> {
                DecRes<?> dec = notification.getValue();
                if (dec != null) {
                    try { dec.close(); } catch (Exception ignored) {}
                }
            })
            .build();
    }
    protected abstract DecRes<?> newDecoder();
    protected abstract void generateMetadata(DecRes<?> metadataDecRes);
}
