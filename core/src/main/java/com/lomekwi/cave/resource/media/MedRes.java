package com.lomekwi.cave.resource.media;

import com.lomekwi.cave.resource.Resource;
import com.lomekwi.cave.resource.decoder.DecRes;

import org.bytedeco.javacv.FrameGrabber;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.Serializable;

/**
 *媒体资源类，指代一个在磁盘中存在，占有编解码器的资源
 */
public abstract class MedRes implements Resource, Serializable {
    private final String path;//TODO:改为持有输入流
    private transient InputStream inputStream;
    protected transient DecRes<?> decRes;

    public MedRes(String path) throws FileNotFoundException {
        this.path = path;
        this.inputStream = new BufferedInputStream(new FileInputStream(path));
        setupDecoders();
        try {
            decRes.start();
        } catch (FrameGrabber.Exception e) {
            throw new RuntimeException(e);
        }
    }

    public String getPath() {
        return path;
    }
    public DecRes<?> getDecoder(){
        return decRes;
    }

    @Override
    public void close() throws Exception {
        if(decRes !=null){
            decRes.close();
        }
        inputStream.close();
    }

    public InputStream getInputStream() {
        return inputStream;
    }
    private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
        ois.defaultReadObject();
        inputStream = new BufferedInputStream(new FileInputStream(path));
        setupDecoders();
    }
    protected abstract void setupDecoders();
}
