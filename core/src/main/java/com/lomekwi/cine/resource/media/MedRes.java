package com.lomekwi.cine.resource.media;

import com.lomekwi.cine.pipeline.Processor;
import com.lomekwi.cine.resource.Resource;
import com.lomekwi.cine.resource.decoder.DecRes;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

/**
 *媒体资源类，指代一个在磁盘中存在，占有编解码器的资源
 */
public abstract class MedRes implements Resource {
    private final String path;//TODO:改为持有输入流
    private final InputStream inputStream;
    protected DecRes<?> decRes;

    public MedRes(String path) throws FileNotFoundException {
        this.path = path;
        this.inputStream = new BufferedInputStream(new FileInputStream(path));
    }

    public String getPath() {
        return path;
    }
    public DecRes<?> getDecoder(){
        return decRes;
    }
    public abstract Processor getNextProcessor();

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
}
