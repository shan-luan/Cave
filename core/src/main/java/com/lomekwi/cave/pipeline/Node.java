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
            out.unlinkAll();
        }
    }
    protected <P extends InPort<?>> P addInPort(P p){
        inPorts.add(p);
        p.setOwner(this);
        return p;
    }
    protected <P extends OutPort<?>> P addOutPort(P p){
        outPorts.add(p);
        p.setOwner(this);
        return p;
    }
    public abstract String getName();

    public List<InPort<?>> getInPorts(){
        return inPorts;
    }
    public List<OutPort<?>> getOutPorts(){
        return outPorts;
    }


    public abstract static class InPort<T> implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private final String name;

        private T defaultData;

        private OutPort<? extends T> prev;

        private Node owner;

        protected InPort(String name) {
            this.name = name;
        }

        void setOwner(Node owner) {
            this.owner = owner;
        }

        /** @return 拥有此输入端口的节点 */
        public Node getOwner() {
            return owner;
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
        public abstract Set<Class<?>> getConstraint();

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
        protected boolean linkFrom(OutPort<?> p) {
            if (!canLinkFrom(p)) {
                return false;
            }

            unlink();

            setPrev((OutPort<? extends T>) p);
            p.addNext(this);

            return true;
        }

        protected void unlink() {
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


    public abstract static class OutPort<T> implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private final String name;

        protected final Set<InPort<? super T>> next = new HashSet<>();

        private Node owner;

        protected OutPort(String name) {
            this.name = name;
        }

        void setOwner(Node owner) {
            this.owner = owner;
        }

        /** @return 拥有此输出端口的节点 */
        public Node getOwner() {
            return owner;
        }

        public abstract T getData();

        public abstract Class<? extends T> getType();

        public boolean canLinkTo(InPort<?> p) {
            return p.canLinkFrom(this);
        }

        protected boolean linkTo(InPort<?> p) {
            return p.linkFrom(this);
        }

        @SuppressWarnings("unchecked")
        private void addNext(InPort<?> p) {
            next.add((InPort<? super T>) p);
        }

        private void removeNext(InPort<?> p) {
            next.remove(p);
        }

        protected void unlink(InPort<?> p) {
            if (next.contains(p)) {
                p.unlink();
            }
        }

        protected void unlinkAll() {
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
