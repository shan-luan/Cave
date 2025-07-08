package com.lomekwi.cine.resource;

import com.lomekwi.cine.content.VideoClip;

import java.util.HashSet;
import java.util.Set;

//TODO:此类的意图是管理一个来自磁盘（或其它源）的视频的资源。可以根据请求获取多个解码器等资源。当被资源面板删除时通知clips无效化。
public class Video {
    private final String path;
    private final Set<VideoClip> clips=new HashSet<>();

    public Video(String path) {
        this.path = path;
    }

    public String getPath() {
        return path;
    }
    public void dispose() {

    }
    public void addClip(VideoClip clip) {
        clips.add(clip);
    }
    public void removeClip(VideoClip clip) {
        clips.remove(clip);
    }
}
