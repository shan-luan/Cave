package com.lomekwi.cave.element;

import com.lomekwi.cave.pipeline.Filter;
import com.lomekwi.cave.pipeline.Source;
import com.lomekwi.cave.pipeline.Product;

import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


/**被过滤的源
 *
 * @param <T> “获取”此源时返回的产品类型
 */
public class FilteredSrc<T extends Product> implements Source<T> {
    private static final long serialVersionUID = 1L;
    private final Source<T> source;
    private final List<Filter<? super T>> filters;

/**
 *
 * @param source 源
 * @param filters 过滤器,应用顺序为添加顺序，从左到右
 */
@SafeVarargs
   public FilteredSrc(@NonNull Source<T> source, @NonNull Filter<? super T>... filters) {
       this.source = source;
       this.filters = new ArrayList<>(Arrays.asList(filters));
   }
   public T get(long time){
       T product = source.get(time);
       for (Filter<? super T> filter : filters) {
           filter.filter(product);
       }
       return product;
   }
   public List<Filter<? super T>> getFilters() {
       return filters;
   }
   public Source<T> getSource() {
       return source;
   }
}
