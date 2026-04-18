package com.lomekwi.cave.pipeline;

@SuppressWarnings("InstantiationOfUtilityClass")
public class PipelineEvents {
    public static class LastFrameEndEvent {
        public static final LastFrameEndEvent INSTANCE = new LastFrameEndEvent();
        private LastFrameEndEvent() {
        }
    }
}
