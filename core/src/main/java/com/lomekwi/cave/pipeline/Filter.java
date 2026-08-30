package com.lomekwi.cave.pipeline;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

/**
 * 过滤器节点：单一 FilterIn/FilterOut，可挂载到某个 {@link Source} 的 filter 链上。
 *
 * @author shan_luan_
 */
public abstract class Filter<T> extends Node implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private FilterIn filterIn;
    private FilterOut filterOut;

    /** 所属源（当此 filter 挂载到某个 Source 的链上时）；未挂载时为 null。 */
    private Source<?> source;

    /** 挂载到指定源。由 {@link FilterList} 在添加/移除 filter 时维护。 */
    void setSource(Source<?> source) {
        this.source = source;
    }

    public Source<?> getSource() {
        return source;
    }
    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    protected <P extends InPort<?>> P addInPort(P p) {
        P port = super.addInPort(p);
        if (p instanceof Filter.FilterIn in) {
            if (filterIn != null) {
                throw new IllegalStateException("只能有一个过滤输入端口.");
            }
            filterIn = in;
        }
        return port;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    protected <P extends OutPort<?>> P addOutPort(P p) {
        P port = super.addOutPort(p);
        if (p instanceof Filter.FilterOut out) {
            if (filterOut != null) {
                throw new IllegalStateException("只能有一个过滤输出端口.");
            }
            filterOut = out;
        }
        return port;
    }
    public abstract Class<T> getType();
    public FilterIn getFilterIn(){
        return filterIn;
    }
    public FilterOut getFilterOut(){
        return filterOut;
    }

    public abstract class FilterIn extends InPort<T>{
        protected FilterIn(String name) {
            super(name);
        }

        @Override
        public final Set<Class<?>> getConstraint(){
            if(getFilterOut().isLinked()){
                Set<Class<?>> c = new HashSet<>();
                for(var nextIn : getFilterOut().getNext()){
                    c.addAll(nextIn.getConstraint());
                }
                c.add(Filter.this.getType());
                return c;
            }else {
                return Set.of(Filter.this.getType());
            }
        }
    }
    public abstract class FilterOut extends OutPort<T>{
        protected FilterOut(String name) {
            super(name);
        }

        /**
         * @return 输入已连接时返回上游实际类型,否则返回 null 表示类型未知.
         */
        @Override
        public Class<? extends T> getType(){
            if(getFilterIn().isLinked()){
                return getFilterIn().getPrev().getType();
            }else {
                return null;
            }
        }
    }
}
