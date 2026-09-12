package com.lomekwi.cave.pipeline;

import java.io.Serial;

//TODO:WIP
public class NodeGraphFilter extends Filter<Object>{
    @Serial
    private static final long serialVersionUID = 1L;

    private final Sink innerSink = new Sink();
    private final NodeGraph innerNodes = new NodeGraph();
    {
        innerNodes.add(innerSink);

        addInPort(new FilterIn());
        addInPort(new InPort<>("节点图", innerNodes, NodeGraph.class));
        addOutPort(new FilterOut() {
            @Override
            public Object getData() {
                return innerSink.get();
            }
        });
    }
    public NodeGraph getInnerNodes() {
        return innerNodes;
    }

    @Override
    public String getName() {
        return "节点图";
    }

    @Override
    public Class<Object> getType() {
        return Object.class;
    }
}
