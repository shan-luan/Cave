package com.lomekwi.cave.pipeline;

@SuppressWarnings("InstantiationOfUtilityClass")
public class PipelineEvents {
    public static class productsDoneEvent {
        public static final productsDoneEvent INSTANCE = new productsDoneEvent();
        private productsDoneEvent() {
        }
    }
}
