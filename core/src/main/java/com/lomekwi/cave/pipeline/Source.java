package com.lomekwi.cave.pipeline;

import com.lomekwi.cave.timeline.Segment;
import com.lomekwi.cave.timeline.Track;
import com.lomekwi.cave.ui.editpanel.detail.SourceActor;
import com.lomekwi.cave.ui.editpanel.tlarea.SegActor;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 帧源。仅应该被单个片段访问。
 * @param <T>
 */
public abstract class Source<T extends Frame> implements Serializable {
    protected transient T frame;
    @Serial
    private static final long serialVersionUID = 1L;
    private final List<Modifier<? super T>> modifiers = new ArrayList<>();
    private transient Segment segment;

    /**
     * 同步到指定时间
     * @param time 绝对时间
     */
    public abstract void sync(long time, Track track) throws Exception;

    /**
     *  获取指定时间的产品
     * @param time 绝对时间
     * @return 产品
     */
    public final T get(long time, Track track){
        T frame = generate(time, track);
        for (Modifier<? super T> modifier : modifiers) {
            frame = modifier.modify(frame, time);
        }
        return frame;
    }
    /**
     * 建议进行预取数据的耗时操作。
     */
    public void prefetch(){};
    protected abstract T generate(long time, Track track);
    public List<Modifier<? super T>> getModifiers() {
            return modifiers;
        }
    public Source<T> attach(Modifier<? super T> modifier){
       modifiers.add(modifier);
            return this;
    }
    public Source() {
    }

    public Segment getSegment() {
        return segment;
    }

    public void setSegment(Segment segment) {
        this.segment = segment;
    }

    public abstract long getLengthPerExportFrame();
    /** 媒体源的总时长（微秒） */
    public abstract long getDuration();
    public abstract String getDisplayName();
    public abstract Class<T> getFrameType();
    public void onDuplicate(Source<?> original){
    }
    public abstract SourceActor getSourceActor();
    public abstract SegActor createSegActor(Segment segment);
}
