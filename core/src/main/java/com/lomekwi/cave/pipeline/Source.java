package com.lomekwi.cave.pipeline;

import com.lomekwi.cave.timeline.Track;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public abstract class Source implements Serializable {
    private static final long serialVersionUID = 1L;
        private final List<Filter> filters = new ArrayList<>();

    /**
     *  获取指定时间的产品
     * @param time 绝对时间
     * @return 产品
     */
    public final Product get(long time, Track track){
            Product product = generate(time, track);
            for (Filter filter : filters) {
                filter.filter(product);
            }
            return product;
        }
        protected abstract Product generate(long time, Track track);
        public List<Filter> getFilters() {
            return filters;
        }
        public Source attach(Filter filter){
            filters.add(filter);
            return this;
        }
}
