package com.lomekwi.cave.pipeline;

import static com.lomekwi.cave.pipeline.Node.*;

//TODO:WIP
public class NodeGraph extends Filter<Object>{
    {
        addInPort(new FilterIn());
        addOutPort(new FilterOut() {
            @Override
            public Object getData() {
                return null;
            }
        });
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
