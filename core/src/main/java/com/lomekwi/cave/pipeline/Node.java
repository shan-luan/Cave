package com.lomekwi.cave.pipeline;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author shan_luan_
 */
public abstract class Node implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final List<InPort<?>> inPorts = new ArrayList<>();
    private final List<OutPort<?>> outPorts = new ArrayList<>();

    protected void remove() {
        for (InPort<?> in : inPorts) {
            in.unlink();
        }

        for (OutPort<?> out : outPorts) {
            out.unlink();
        }
    }
    protected <P extends InPort<?>> P addInPort(P p){
        inPorts.add(p);
        return p;
    }
    protected <P extends OutPort<?>> P addOutPort(P p){
        outPorts.add(p);
        return p;
    }
    public abstract String getName();

    public List<InPort<?>> getInPorts(){
        return inPorts;
    }
    public List<OutPort<?>> getOutPorts(){
        return outPorts;
    }

    public sealed interface Port extends Serializable permits InPort, OutPort {
        boolean link(Port target);
        void unlink();
        default String getName(){
            return toString();
        }
    }
    public non-sealed class InPort<T> implements Port {
        @Serial
        private static final long serialVersionUID = 1L;

        private final String name;

        private final Set<Class<?>> constraint;

        private T defaultData;

        private OutPort<? extends T> prev;

        @Override
        public boolean link(Port target) {
            if (!(target instanceof OutPort<?> out)) {
                return false;
            }
            return linkFrom(out);
        }

        public InPort(String name, Class<?>... constraint) {
            this.name = name;
            this.constraint = Set.of(constraint);
        }

        public InPort(String name, T defaultValue, Class<?>... constraint) {
            this(name, constraint);
            this.defaultData = defaultValue;
        }

        public OutPort<? extends T> getPrev() {
            return prev;
        }

        private void setPrev(OutPort<? extends T> prev) {
            this.prev = prev;
        }

        public T getData() {
            return prev == null ? getDefaultData() : prev.getData();
        }

        public T getDefaultData(){
            return defaultData;
        };

        public void setDefaultData(T data) {
            defaultData=data;
        }

        /**
         * @return 可以连接到此输入端口的输出端口所需要满足的全部约束.即交叉类型(&).
         */
        public Set<Class<?>> getConstraint() {
            return constraint;
        }

        public boolean canLinkFrom(OutPort<?> p) {
            Class<?> outType = p.getType();

            if (outType == null) {
                return true;
            }

            for (Class<?> c : getConstraint()) {
                if (!c.isAssignableFrom(outType)) {
                    return false;
                }
            }

            return true;
        }

        @SuppressWarnings("unchecked")
        public boolean linkFrom(OutPort<?> p) {
            if (!canLinkFrom(p)) {
                return false;
            }

            unlink();

            setPrev((OutPort<? extends T>) p);
            p.addNext(this);

            return true;
        }

        @Override
        public void unlink() {
            if (prev != null) {
                prev.removeNext(this);
                prev = null;
            }
        }
        public boolean isLinked(){
            return prev!=null;
        }
        public String getName() {
            return name;
        }
    }


    public non-sealed abstract class OutPort<T> implements Port {
        @Override
        public boolean link(Port target) {
            if (!(target instanceof InPort<?> in)) {
                return false;
            }
            return linkTo(in);
        }

        @Serial
        private static final long serialVersionUID = 1L;

        private final String name;

        private final Class<? extends T> type;

        protected final Set<InPort<? super T>> next = new HashSet<>();

        protected OutPort(String name, Class<? extends T> type) {
            this.name = name;
            this.type = type;
        }

        public abstract T getData();

        public Class<? extends T> getType() {
            return type;
        }

        public boolean canLinkTo(InPort<?> p) {
            return p.canLinkFrom(this);
        }

        public boolean linkTo(InPort<?> p) {
            return p.linkFrom(this);
        }

        @SuppressWarnings("unchecked")
        private void addNext(InPort<?> p) {
            next.add((InPort<? super T>) p);
        }

        private void removeNext(InPort<?> p) {
            next.remove(p);
        }

        public void unlink(InPort<?> p) {
            if (next.contains(p)) {
                p.unlink();
            }
        }

        @Override
        public void unlink() {
            for (InPort<?> p : Set.copyOf(next)) {
                p.unlink();
            }

            next.clear();
        }

        public boolean isLinked(){
            return !next.isEmpty();
        }

        public Set<InPort<? super T>> getNext(){
            return next;
        }
        public String getName() {
            return name;
        }
    }
}
