package com.lomekwi.cave.pipeline;

import com.google.common.graph.AbstractNetwork;
import com.google.common.graph.ElementOrder;
import com.google.common.graph.EndpointPair;
import com.google.common.graph.MutableNetwork;

import org.jspecify.annotations.NullMarked;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * 以 {@link Node} 为元素的节点网络。
 *
 * <p>本网络并不单独维护一份邻接结构，而是直接读取 {@link Node} 自身对端口连接的维护
 * （每个 {@link Node.InPort} 的 prev、每个 {@link Node.OutPort} 的 next）。因此网络的边
 * 就是「已处于连接状态的下游 InPort」：一条有向边 u→v 对应 v 的某个已连接的 InPort。
 * 由于两条节点之间可以存在多个这样的端口连接，故本网络允许平行边。</p>
 */
@NullMarked
@SuppressWarnings("UnstableApiUsage")
public class NodeGraph extends AbstractNetwork<Node, Node.InPort<?>>
        implements MutableNetwork<Node, Node.InPort<?>> {

    private final Set<Node> nodes = new HashSet<>();

    public Set<Node> getNodes() {
        return nodes;
    }

    // ------------------------------------------------------------------
    // Network 的只读部分
    // ------------------------------------------------------------------

    @Override
    public Set<Node> nodes() {
        return nodes;
    }

    @Override
    public Set<Node.InPort<?>> edges() {
        Set<Node.InPort<?>> result = new HashSet<>();
        for (Node node : nodes) {
            result.addAll(inEdges(node));
        }
        return result;
    }

    @Override
    public boolean isDirected() {
        return true;
    }

    @Override
    public boolean allowsParallelEdges() {
        return true;
    }

    @Override
    public boolean allowsSelfLoops() {
        return false;
    }

    @Override
    public ElementOrder<Node> nodeOrder() {
        return ElementOrder.unordered();
    }

    @Override
    public ElementOrder<Node.InPort<?>> edgeOrder() {
        return ElementOrder.unordered();
    }

    @Override
    public Set<Node> adjacentNodes(Node node) {
        Set<Node> result = new HashSet<>(predecessors(node));
        result.addAll(successors(node));
        return result;
    }

    @Override
    public Set<Node> predecessors(Node node) {
        Set<Node> result = new HashSet<>();
        for (Node.InPort<?> in : node.getInPorts()) {
            Node.OutPort<?> prev = in.getPrev();
            if (prev != null) {
                Node owner = prev.getOwner();
                if (owner != null && nodes.contains(owner)) {
                    result.add(owner);
                }
            }
        }
        return result;
    }

    @Override
    public Set<Node> successors(Node node) {
        Set<Node> result = new HashSet<>();
        for (Node.OutPort<?> out : node.getOutPorts()) {
            for (Node.InPort<?> next : out.getNext()) {
                Node owner = next.getOwner();
                if (owner != null && nodes.contains(owner)) {
                    result.add(owner);
                }
            }
        }
        return result;
    }

    @Override
    public Set<Node.InPort<?>> incidentEdges(Node node) {
        Set<Node.InPort<?>> result = new HashSet<>(inEdges(node));
        result.addAll(outEdges(node));
        return result;
    }

    @Override
    public Set<Node.InPort<?>> inEdges(Node node) {
        Set<Node.InPort<?>> result = new HashSet<>();
        for (Node.InPort<?> in : node.getInPorts()) {
            if (in.isLinked()) {
                result.add(in);
            }
        }
        return result;
    }

    @Override
    public Set<Node.InPort<?>> outEdges(Node node) {
        Set<Node.InPort<?>> result = new HashSet<>();
        for (Node.OutPort<?> out : node.getOutPorts()) {
            result.addAll(out.getNext());
        }
        return result;
    }

    @Override
    public Set<Node.InPort<?>> edgesConnecting(Node nodeU, Node nodeV) {
        Set<Node.InPort<?>> result = new HashSet<>();
        for (Node.InPort<?> in : nodeV.getInPorts()) {
            Node.OutPort<?> prev = in.getPrev();
            if (prev != null && prev.getOwner() == nodeU) {
                result.add(in);
            }
        }
        return result;
    }

    @Override
    public EndpointPair<Node> incidentNodes(Node.InPort<?> edge) {
        Node nodeV = Objects.requireNonNull(edge.getOwner(), () -> "edge is not in this network: " + edge);
        Node.OutPort<?> prev = Objects.requireNonNull(edge.getPrev(), () -> "edge is not connected: " + edge);
        Node nodeU = Objects.requireNonNull(prev.getOwner(), () -> "edge is not in this network: " + edge);
        return EndpointPair.ordered(nodeU, nodeV);
    }

    // ------------------------------------------------------------------
    // MutableNetwork 的变更部分
    // ------------------------------------------------------------------

    @Override
    public boolean addNode(Node node) {
        return nodes.add(node);
    }

    @Override
    public boolean addEdge(Node nodeU, Node nodeV, Node.InPort<?> edge) {
        if (edge.getOwner() != nodeV) {
            throw new IllegalArgumentException("edge 必须属于 nodeV: " + edge);
        }
        if (edge.isLinked()) {
            throw new IllegalArgumentException("该端口已连接，无法作为新边添加: " + edge);
        }
        for (Node.OutPort<?> out : nodeU.getOutPorts()) {
            if (connect(out, edge)) {
                return true;
            }
        }
        throw new IllegalArgumentException("nodeU 上没有与 edge 兼容的输出端口: " + nodeU.getName());
    }

    /**
     * 精确地把 out 端口连接到 in 端口。约束校验由 {@code linkFrom} 内部完成，
     * 返回 false 表示两端口类型不兼容（此时不产生任何副作用）。
     * 两端所属节点会自动加入图中。
     *
     * <p>若 in 端口此前已连着别的输出端口，会被重连（原连接自动断开）。</p>
     */
    public boolean connect(Node.OutPort<?> out, Node.InPort<?> in) {
        Node nodeU = out.getOwner();
        Node nodeV = in.getOwner();
        if (nodeU == null || nodeV == null) {
            return false;
        }
        addNode(nodeU);
        addNode(nodeV);
        return in.linkFrom(out);
    }

    @Override
    public boolean addEdge(EndpointPair<Node> endpoints, Node.InPort<?> edge) {
        if (!isOrderingCompatible(endpoints)) {
            throw new IllegalArgumentException("Endpoints must be ordered for a directed graph: " + endpoints);
        }
        return addEdge(endpoints.nodeU(), endpoints.nodeV(), edge);
    }

    @Override
    public boolean removeNode(Node node) {
        if (!nodes.contains(node)) {
            return false;
        }
        node.remove();
        nodes.remove(node);
        return true;
    }

    @Override
    public boolean removeEdge(Node.InPort<?> edge) {
        if (edge.getOwner() == null || !edge.isLinked()) {
            return false;
        }
        edge.unlink();
        return true;
    }
}