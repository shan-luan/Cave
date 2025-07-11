package com.lomekwi.cine.resource;

import com.lomekwi.cine.content.VideoClip;
import com.lomekwi.cine.pipeline.decode.VideoDecoder;

import java.util.HashSet;
import java.util.Set;

//TODO:此类的意图是管理一个来自磁盘（或其它源）的视频的资源。可以根据请求获取多个解码器等资源。当被资源面板删除时通知clips无效化。
public class Video implements Media{
    private final String path;
    private final VideoDecoder decoder;
    private final Set<VideoClip> clips = new HashSet<>();

    public Video(String path) {
        this.path = path;
        decoder = new VideoDecoder(this);
    }

    public String getPath() {
        return path;
    }

    public void dispose() {
        decoder.dispose();
    }

    public VideoDecoder getDecoder() {
        return decoder;
    }
    public void add(VideoClip clip) {
        clips.add(clip);
    }

    public void remove(VideoClip clip) {
        clips.remove(clip);
    }
}
