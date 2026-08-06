package com.lomekwi.cave.node;

import java.util.HashSet;
import java.util.Set;

public abstract class Filter<T> extends Node{
    private FilterIn filterIn;
    private FilterOut filterOut;
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
        /**
         * @return 输入已连接时返回上游实际类型,否则返回 null 表示类型未知.
         */
        @Override
        public final Class<? extends T> getType(){
            if(getFilterIn().isLinked()){
                return getFilterIn().getPrev().getType();
            }else {
                return null;
            }
        }
    }
}
