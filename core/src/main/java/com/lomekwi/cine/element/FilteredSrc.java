package com.lomekwi.cine.element;

import com.lomekwi.cine.pipeline.Filter;
import com.lomekwi.cine.pipeline.Source;
import com.lomekwi.cine.pipeline.Product;

import org.jspecify.annotations.NonNull;


/**被过滤的源
 *
 * @param <T> “获取”此源时返回的产品类型
 */
public class FilteredSrc<T extends Product> implements Source<T> {
    private final Source<T> source;
    private final Filter<? super T>[] filters;

/**
 *
 * @param source 源
 * @param filters 过滤器,应用顺序为添加顺序，从左到右
 */
@SafeVarargs
   public FilteredSrc(@NonNull Source<T> source, @NonNull Filter<? super T>... filters) {
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
   public Source<T> getSource() {
       return source;
   }
}
