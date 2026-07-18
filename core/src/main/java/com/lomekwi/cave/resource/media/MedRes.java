package com.lomekwi.cave.resource.media;

import com.lomekwi.cave.pipeline.Frame;
import com.lomekwi.cave.resource.Resource;
import com.lomekwi.cave.resource.decoder.DecRes;

import com.badlogic.gdx.utils.IntMap;

import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.io.Serializable;

/**
 *媒体资源类，指代一个在磁盘中存在，占有编解码器的资源
 */
public abstract class MedRes implements Resource, Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private final String path;
    protected long duration;
    protected @Nullable String codecName;
    protected transient IntMap<DecRes<?>> decRes = new IntMap<>();

    /**
     * 必须确保路径对应一个存在的文件
     */
    public MedRes(String path) {
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
    public DecRes<?> getDecoder(int trackIndex){
        DecRes<?> decoder = decRes.get(trackIndex);
        if(decoder==null){
            decoder=newDecoder();
            decRes.put(trackIndex, decoder);
        }
        return decoder;
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
        for(var decoder : decRes.values()) {
            decoder.close();
        }
    }

    @Serial
    private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
        ois.defaultReadObject();
        decRes = new IntMap<>();
    }
    protected abstract DecRes<?> newDecoder();
    protected abstract void generateMetadata(DecRes<?> metadataDecRes);
}
