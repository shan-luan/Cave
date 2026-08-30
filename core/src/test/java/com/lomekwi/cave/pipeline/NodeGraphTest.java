package com.lomekwi.cave.pipeline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 验证 {@link NodeGraph}：连通关系全部派生自 Node 的端口链接。
 *
 * 覆盖：
 * 1) addNode / nodes / getNodes；
 * 2) addEdge 建立前驱与后继、incidentNodes 端点、adjacentNodes；
 * 3) 平行边（同一对节点间的多条端口连接）；
 * 4) edges / inEdges / outEdges / edgesConnecting / hasEdgeConnecting；
 * 5) removeEdge 断开、removeNode 连带断开其端口链接；
 * 6) addEdge 的非法输入（重复边、端口不属于目标节点）。
 */
public class NodeGraphTest {

    /** 带若干个 Num 输入/输出端口的最小节点。 */
    /** 带一个约束可定制的输入端口的最小节点。 */
    static final class ConstraintedNode extends Node {
        private final Node.InPort<?> in;
        private final String name;

        ConstraintedNode(String name, Set<Class<?>> constraint) {
            this.name = name;
            this.in = addInPort(new Node.InPort<Void>("in") {
                @Override
                public Set<Class<?>> getConstraint() {
                    return constraint;
                }
            });
        }

        @Override
        public String getName() {
            return name;
        }

        Node.InPort<?> inPort() {
            return in;
        }
    }

    static final class NumNode extends Node {
        private final List<NumInPort> ins = new ArrayList<>();
        private final List<NumOutPort> outs = new ArrayList<>();
        private final String name;

        NumNode(String name, int inCount, int outCount) {
            this.name = name;
            for (int i = 0; i < inCount; i++) {
                ins.add(addInPort(new NumInPort("in" + i)));
            }
            for (int i = 0; i < outCount; i++) {
                final int val = i;
                outs.add(addOutPort(new NumOutPort("out" + i) {
                    @Override
                    protected double getVal() {
                        return val;
                    }
                }));
            }
        }

        @Override
        public String getName() {
            return name;
        }

        NumInPort in(int i) {
            return ins.get(i);
        }

        NumOutPort out(int i) {
            return outs.get(i);
        }
    }

    @Test
    public void addNode_registersNodeOnce() {
        NodeGraph g = new NodeGraph();
        NumNode a = new NumNode("a", 1, 1);

        assertTrue(g.addNode(a));
        assertFalse(g.addNode(a)); // 重复添加返回 false
        assertEquals(Set.of(a), g.nodes());
        assertEquals(Set.of(a), g.getNodes());
    }

    @Test
    public void addEdge_linksPredAndSucc() {
        NodeGraph g = new NodeGraph();
        NumNode a = new NumNode("a", 0, 1);
        NumNode b = new NumNode("b", 1, 0);

        assertTrue(g.addEdge(a, b, b.in(0)));

        // 端点已是图中的节点（addEdge 会自动加入）
        assertEquals(Set.of(a, b), g.nodes());

        assertEquals(Set.of(a), g.predecessors(b));
        assertEquals(Set.of(b), g.successors(a));
        // adjacentNodes 是前驱∪后继，不含自身：a 无前驱，仅后继 b
        assertEquals(Set.of(b), g.adjacentNodes(a));

        // 边的两个端点：a→b（有序对有向边）
        var pair = g.incidentNodes(b.in(0));
        assertEquals(a, pair.nodeU());
        assertEquals(b, pair.nodeV());
    }

    @Test
    public void parallelEdges_betweenSamePair() {
        NodeGraph g = new NodeGraph();
        NumNode a = new NumNode("a", 0, 2);
        NumNode b = new NumNode("b", 2, 0);

        assertTrue(g.addEdge(a, b, b.in(0)));
        assertTrue(g.addEdge(a, b, b.in(1)));

        assertEquals(Set.of(a), g.predecessors(b));
        assertEquals(Set.of(b), g.successors(a));
        // 平行边：edgesConnecting 返回两条
        assertEquals(Set.of(b.in(0), b.in(1)), g.edgesConnecting(a, b));
        assertTrue(g.hasEdgeConnecting(a, b));
    }

    @Test
    public void edgeSets_reportLinkedInPorts() {
        NodeGraph g = new NodeGraph();
        NumNode a = new NumNode("a", 0, 1);
        NumNode b = new NumNode("b", 1, 0);

        assertTrue(g.addEdge(a, b, b.in(0)));

        assertEquals(Set.of(b.in(0)), g.edges());
        assertEquals(Set.of(b.in(0)), g.inEdges(b));
        assertEquals(Set.of(b.in(0)), g.outEdges(a));
        assertEquals(Set.of(b.in(0)), g.incidentEdges(a));
        assertEquals(Set.of(b.in(0)), g.incidentEdges(b));
    }

    @Test
    public void removeEdge_disconnectsPredAndSucc() {
        NodeGraph g = new NodeGraph();
        NumNode a = new NumNode("a", 0, 1);
        NumNode b = new NumNode("b", 1, 0);
        g.addEdge(a, b, b.in(0));

        assertTrue(g.removeEdge(b.in(0)));
        assertTrue(g.predecessors(b).isEmpty());
        assertTrue(g.successors(a).isEmpty());
        assertTrue(g.edges().isEmpty());
        assertEquals(Set.of(a, b), g.nodes()); // 节点仍然保留
    }

    @Test
    public void removeNode_disconnectsItsLinkedPorts() {
        NodeGraph g = new NodeGraph();
        NumNode a = new NumNode("a", 1, 0);
        NumNode b = new NumNode("b", 0, 1);
        g.addEdge(b, a, a.in(0)); // b→a

        assertTrue(g.removeNode(b));
        assertFalse(g.nodes().contains(b));
        // b 被移除时其输出端口被 unlinkAll，a 不再有前驱
        assertTrue(g.predecessors(a).isEmpty());
        assertTrue(g.edges().isEmpty());
    }

    @Test
    public void connect_bindsSpecificOutPort() {
        NodeGraph g = new NodeGraph();
        NumNode a = new NumNode("a", 0, 2);
        NumNode b = new NumNode("b", 1, 0);

        assertTrue(g.connect(a.out(1), b.in(0)));
        // 精确绑定到指定的输出端口，而不是第一个兼容端口
        assertEquals(a.out(1), b.in(0).getPrev());
        assertEquals(Set.of(a), g.predecessors(b));
        assertEquals(Set.of(b), g.successors(a));
    }

    @Test
    public void connect_incompatible_returnsFalseWithoutSideEffect() {
        NodeGraph g = new NodeGraph();
        NumNode a = new NumNode("a", 0, 1);
        ConstraintedNode b = new ConstraintedNode("b", Set.of(String.class)); // 不接受 NumFrame

        assertFalse(g.connect(a.out(0), b.inPort()));
        // 端点自动加入图中，但类型不兼容未连成边
        assertTrue(g.nodes().contains(a));
        assertTrue(g.nodes().contains(b));
        assertTrue(g.edges().isEmpty());
        assertTrue(g.predecessors(b).isEmpty());
    }

    @Test
    public void addEdge_withLinkedInPort_throws() {
        NodeGraph g = new NodeGraph();
        NumNode a = new NumNode("a", 0, 1);
        NumNode b = new NumNode("b", 1, 0);
        g.addEdge(a, b, b.in(0));

        try {
            g.addEdge(a, b, b.in(0));
            fail("同一已连接的 InPort 不应能再次作为新边添加");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    @Test
    public void addEdge_withWrongOwner_throws() {
        NodeGraph g = new NodeGraph();
        NumNode a = new NumNode("a", 0, 1);
        NumNode b = new NumNode("b", 1, 0);
        NumNode c = new NumNode("c", 1, 0);

        try {
            // edge=c.in(0) 属于 c，不属于 nodeV=b
            g.addEdge(a, b, c.in(0));
            fail("edge 不属于 nodeV 时 addEdge 应抛异常");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    @Test
    public void addEdge_fromNodeWithoutOutPort_throws() {
        NodeGraph g = new NodeGraph();
        NumNode a = new NumNode("a", 0, 0); // 没有输出端口
        NumNode b = new NumNode("b", 1, 0);

        try {
            g.addEdge(a, b, b.in(0));
            fail("nodeU 没有可用的输出端口时 addEdge 应抛异常");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }
}