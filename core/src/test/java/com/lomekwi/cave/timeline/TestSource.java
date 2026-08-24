package com.lomekwi.cave.timeline;

import com.lomekwi.cave.pipeline.Frame;
import com.lomekwi.cave.pipeline.Source;
import com.lomekwi.cave.ui.editpanel.tlarea.SegActor;

/**
 * 测试用最小帧源：仅提供拖拽/轨道逻辑测试所需的时序数据，不涉及真实编解码。
 */
public class TestSource extends Source<TestSource.TestFrame> {
    private final long duration;

    public TestSource(long duration) {
        this.duration = duration;
    }

    @Override
    public void sync(long time, Track track) throws Exception {
        // no-op
    }

    @Override
    protected TestFrame generate(long time, Track track) {
        return new TestFrame(track);
    }

    @Override
    public long getLengthPerExportFrame() {
        return 1;
    }

    @Override
    public long getDuration() {
        return duration;
    }

    @Override
    public String getDisplayName() {
        return "test";
    }

    @Override
    public Class<TestFrame> getFrameType() {
        return TestFrame.class;
    }

    @Override
    public SegActor createSegActor(Segment segment) {
        return new TestSegActor(segment);
    }

    public static class TestFrame extends Frame {
        public TestFrame(Track track) {
            super(track);
        }
    }
}
