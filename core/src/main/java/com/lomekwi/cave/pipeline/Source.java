package com.lomekwi.cave.pipeline;

import com.lomekwi.cave.timeline.Track;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 帧源。仅应该被单个片段访问。
 * @param <T>
 */
public abstract class Source<T extends Frame> implements Serializable {
    protected transient T frame;
    @Serial
    private static final long serialVersionUID = 1L;
    private final List<Filter<? super T>> filters = new ArrayList<>();

    /**
     *  获取指定时间的产品
     * @param time 绝对时间
     * @return 产品
     */
    public final T get(long time, Track track){
        T product = generate(time, track);
        for (Filter<? super T> filter : filters) {
            filter.filter(product);
        }
        return product;
    }
    /**
     * 建议进行预取数据的耗时操作。
     */
    public void prefetch(){};
    protected abstract T generate(long time, Track track);
    public List<Filter<? super T>> getFilters() {
            return filters;
        }
    public Source<T> attach(Filter<? super T> filter){
       filters.add(filter);
            return this;
    }
    public Source() {
    }

    public abstract long getLengthPerExportFrame();
}
