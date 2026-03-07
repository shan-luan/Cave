package com.lomekwi.cave.pipeline;

import java.io.Serializable;

public interface Source<T extends Product> extends Serializable {
    /**
     * 获取指定时间处的产品
     * @param time 相对于源起点的本地时间，如对于时间轴上的片段是在时间轴的起点为0，对于媒体则是媒体开始播放的起点为0
     * @return 产品
     */
    public T get(long time);
}
