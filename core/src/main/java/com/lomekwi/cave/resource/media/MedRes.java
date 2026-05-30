package com.lomekwi.cave.resource.media;

import com.lomekwi.cave.resource.Resource;
import com.lomekwi.cave.resource.decoder.DecRes;

import org.bytedeco.javacv.FrameGrabber;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;

/**
 *媒体资源类，指代一个在磁盘中存在，占有编解码器的资源
 */
public abstract class MedRes implements Resource, Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private final String path;
    protected transient ArrayList<DecRes<?>> decRes=new ArrayList<>();
    protected transient DecRes<?> metadataDecRes;

    /**
     * 必须确保路径对应一个存在的文件
     */
    public MedRes(String path) {
        this.path = path;
        try {
            metadataDecRes = newDecoder();
            metadataDecRes.start();
        } catch (FrameGrabber.Exception e) {
            throw new RuntimeException(e);
        }
    }

    public String getPath() {
        return path;
    }
    public DecRes<?> getDecoder(int trackIndex){
        while(decRes.size()<=trackIndex){
            decRes.add(null);
        }
        var decoder=decRes.get(trackIndex);
        if(decoder==null){
            decoder=newDecoder();
            decRes.set(trackIndex,decoder);
        }
        return decoder;
    }

    @Override
    public void close() throws Exception {
        for(var decoder:decRes){
            if(decoder!=null) {
                decoder.close();
            }
        }
        if(metadataDecRes!=null){
            metadataDecRes.close();
        }
    }

    @Serial
    private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
        ois.defaultReadObject();
        decRes = new ArrayList<>();
        try {
            metadataDecRes = newDecoder();
            metadataDecRes.start();
        } catch (FrameGrabber.Exception e) {
            throw new RuntimeException(e);
        }
    }
    protected abstract DecRes<?> newDecoder();
}
