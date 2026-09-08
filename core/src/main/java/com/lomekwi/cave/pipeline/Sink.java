package com.lomekwi.cave.pipeline;

import org.jspecify.annotations.*;

public final class Sink extends Node{
    private final InPort<Object> in = addInPort(new InPort<>("输入"));
    @Override
    public @NonNull String getName() {
        return "总输出";
    }
    public @Nullable Object get(){
        return in.getData();
    }
}
