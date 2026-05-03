package com.lomekwi.cave.pipeline;

import com.lomekwi.cave.timeline.Track;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public abstract class Source<T extends Frame> implements Serializable {
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
        protected abstract T generate(long time, Track track);
        public List<Filter<? super T>> getFilters() {
            return filters;
        }
        public Source<T> attach(Filter<? super T> filter){
            filters.add(filter);
            return this;
        }
}
