package com.lomekwi.cave.pipeline;

import com.lomekwi.cave.timeline.Segment;
import com.lomekwi.cave.timeline.Track;
import com.lomekwi.cave.ui.editpanel.inspector.SourceActor;
import com.lomekwi.cave.ui.editpanel.tlarea.SegActor;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 帧源。仅应该被单个片段访问。自身是 filter 链的头：
 * 提供 FilterOut（链起点，输出 generate() 生成的帧）。
 *
 * @param <T> 帧类型
 */
@NullMarked
public abstract class Source<T extends Frame> extends Filter<T> implements Serializable {
    protected transient @Nullable T frame;

    /** 链头输出端口：输出本源生成的最新帧，供第一个 filter 消费。 */
    public final FilterOut headOut = addOutPort(new FilterOut() {
        @Override
        public T getData() {
            return frame;
        }

        @Override
        public Class<? extends T> getType() {
            return getFrameType();
        }
    });

    @Serial
    private static final long serialVersionUID = 1L;

    private final List<Filter<? super T>> filters = new FilterList<>(this);

    private transient @Nullable Segment segment;

    /**
     * 同步到指定时间
     * @param time 绝对时间
     */
    public abstract void sync(long time,Track track) throws Exception;

    /**
     * 获取指定时间的产品：生成帧后沿 filter 链（端口连接）求值，
     * 返回链上最后一个 filter 的输出；无 filter 时返回原生帧。
     * @param time 绝对时间
     * @return 产品
     */
    @SuppressWarnings("unchecked")
    public final @Nullable T get(long time, Track track) {
        frame = generate(time, track);
        if (filters.isEmpty()) {
            return frame;
        }
        // 沿 chain：headOut → f1.in → f1.out → ... → 最后一个 filter 的 out
        return (T) filters.get(filters.size() - 1).getFilterOut().getData();
    }

    /**
     * 建议进行预取数据的耗时操作。
     */
    public void prefetch(){}

    protected abstract @Nullable T generate(long time, Track track);

    public List<Filter<? super T>> getFilters() {
        return filters;
    }

    public Source<T> attach(Filter<? super T> filter) {
        filters.add(filter);
        return this;
    }

    public Source() {
    }

    public @Nullable Segment getSegment() {
        return segment;
    }

    public void setSegment(Segment segment) {
        this.segment = segment;
    }

    public abstract long getLengthPerExportFrame();
    /** 媒体源的总时长（微秒） */
    public abstract long getDuration();
    /**
     * 插入时间轴时片段使用的默认时长。时长无界（{@link #getDuration()} 为
     * {@link Long#MAX_VALUE}）的源必须返回有限值。
     */
    public long getDefaultSegmentDuration() {
        return getDuration();
    }
    public abstract String getDisplayName();
    public abstract Class<T> getFrameType();

    @Override
    public Class<T> getType() {
        return getFrameType();
    }
    public void onDuplicate(Source<?> original){
    }
    public SourceActor getSourceActor() {
        return new SourceActor(this);
    }
    public abstract SegActor createSegActor(Segment segment);

    @Override
    public String getName() {
        return getDisplayName();
    }
}
