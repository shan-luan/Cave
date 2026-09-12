package com.lomekwi.cave.pipeline;

import java.io.Serial;
import java.io.Serializable;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

//TODO:WIP
public class NodeGraph extends AbstractSet<Node> implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final Set<Node> delegate = new HashSet<>();

    public NodeGraph() {
    }

    public NodeGraph(Collection<? extends Node> nodes) {
        delegate.addAll(nodes);
    }

    @Override
    public Iterator<Node> iterator() {
        return delegate.iterator();
    }

    @Override
    public int size() {
        return delegate.size();
    }

    @Override
    public boolean contains(Object o) {
        return delegate.contains(o);
    }

    @Override
    public boolean add(Node node) {
        return delegate.add(node);
    }

    @Override
    public boolean remove(Object o) {
        return delegate.remove(o);
    }

    @Override
    public void clear() {
        delegate.clear();
    }
}
