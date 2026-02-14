package com.lomekwi.cine.element;

import com.lomekwi.cine.pipeline.Filter;
import com.lomekwi.cine.pipeline.Producer;
import com.lomekwi.cine.pipeline.Product;

import org.jspecify.annotations.NonNull;


/**Element，表示任何时间线能承载的元素
 *
 * @param <T> “获取”此元素时返回的产品类型
 */
public class Element<T extends Product> implements Producer<T>{
    private final Producer<T> source;
    private final Filter<? super T>[] filters;

/**
 *
 * @param source 元素的源
 * @param filters 元素的过滤器,应用顺序为添加顺序，从左到右
 */
    @SafeVarargs
    public Element(@NonNull Producer<T> source,@NonNull Filter<? super T>... filters) {
        this.source = source;
        this.filters = filters;
    }
    public T get(long time){
        T product = source.get(time);
        for (Filter<? super T> filter : filters) {
            filter.filter(product);
        }
        return product;
    }
    public Filter<? super T>[] getFilters() {
        return filters.clone();
    }

}
