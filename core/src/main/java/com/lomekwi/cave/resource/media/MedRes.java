package com.lomekwi.cave.resource.media;

import com.lomekwi.cave.resource.Resource;
import com.lomekwi.cave.resource.decoder.DecRes;
import com.lomekwi.cave.timeline.Track;

import org.bytedeco.javacv.FrameGrabber;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 *媒体资源类，指代一个在磁盘中存在，占有编解码器的资源
 */
public abstract class MedRes implements Resource, Serializable {
    private static final long serialVersionUID = 1L;
    private final String path;
    protected transient Map<Track,DecRes<?>> decRes=new HashMap<>();

    /**
     * 必须确保路径对应一个存在的文件
     */
    public MedRes(String path) {
        this.path = path;
        try {
            decRes.put(null,newDecoder());
            decRes.get(null).start();
        } catch (FrameGrabber.Exception e) {
            throw new RuntimeException(e);
        }
    }

    public String getPath() {
        return path;
    }
    public DecRes<?> getDecoder(@Nullable Track track){
        return decRes.computeIfAbsent(track, k -> newDecoder());
    }

    @Override
    public void close() throws Exception {
        for(DecRes<?> decoder:decRes.values()){
            decoder.close();
        }
    }

    private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
        ois.defaultReadObject();
        decRes = new HashMap<>();
        try {
            decRes.put(null, newDecoder());
            decRes.get(null).start();
        } catch (FrameGrabber.Exception e) {
            throw new RuntimeException(e);
        }
    }
    protected abstract DecRes<?> newDecoder();
}
