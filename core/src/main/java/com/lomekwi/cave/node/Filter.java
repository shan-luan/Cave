package com.lomekwi.cave.node;

import java.util.HashSet;
import java.util.Set;

public abstract class Filter<T> extends Node{
    private int filterInIdx =-1;
    private int filterOutIdx =-1;
    @Override
    protected int addInPort(InPort<?> p) {
        int idx = super.addInPort(p);
        if (p instanceof Filter<?>.FilterIn) {
            if (filterInIdx != -1) {
                throw new IllegalStateException("只能有一个过滤输入端口.");
            }
            filterInIdx = idx;
        }
        return idx;
    }

    @Override
    protected int addOutPort(OutPort<?> p) {
        int idx = super.addOutPort(p);
        if (p instanceof Filter<?>.FilterOut) {
            if (filterOutIdx != -1) {
                throw new IllegalStateException("只能有一个过滤输出端口.");
            }
            filterOutIdx = idx;
        }
        return idx;
    }
    public abstract Class<T> getType();
    @SuppressWarnings("unchecked")
    public FilterIn getFilterIn(){
        return (FilterIn) getInPort(filterInIdx);
    }
    @SuppressWarnings("unchecked")
    public FilterOut getFilterOut(){
        return (FilterOut) getOutPort(filterOutIdx);
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
