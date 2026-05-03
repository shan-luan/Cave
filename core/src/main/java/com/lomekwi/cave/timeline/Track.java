package com.lomekwi.cave.timeline;

import com.google.common.collect.Range;
import com.google.common.collect.RangeMap;
import com.google.common.collect.TreeRangeMap;
import com.lomekwi.cave.pipeline.Frame;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@NullMarked
public class Track implements Serializable {
    private transient RangeMap<Long, Segment> sources = TreeRangeMap.create();
    private transient Map.@Nullable Entry<Range<Long>, Segment> cache;
    private long length;
    private boolean lengthChanged = true;
    private long @Nullable [] serializationRanges;
    private @Nullable List<Segment> serializationSources;
    private static final long serialVersionUID = 1L;
    public Track() {
    }

    protected void add(Segment segment, long start, long duration) {
        Range<Long> r = Range.closedOpen(start, start + duration);
        sources.put(r, segment);
        segment.setTrack(this);
        segment.setRange(r);
        cache = null;
        lengthChanged = true;
    }
    protected void remove(long start, long duration) {
        sources.remove(Range.closedOpen(start, start + duration));
        cache = null;
        lengthChanged = true;
    }

    public @Nullable Frame get(long time) {
        if (cache == null || !cache.getKey().contains(time)) {
            cache = sources.getEntry(time);
        }
        if(cache == null){
            return null;
        }
        return cache.getValue().get(time, this);
    }
    /**
     * 检查指定范围是否与给定的条目兼容（即该范围是否为空闲或仅被同一片段占用）
     *
     * @param entry 要检查的片段条目，包含时间范围和对应的Segment对象
     * @param range 要检查的时间范围
     * @return 如果范围内没有其他片段占用则返回true；如果范围内只有与entry相同的片段也返回true；否则返回false
     */
    public boolean isFree(Map.Entry<Range<Long>, Segment> entry, Range<Long> range) {
        Map<Range<Long>, Segment> m = sources.subRangeMap(range).asMapOfRanges();
        if(m.size()>1) return false;
        if (m.isEmpty()) return true;
        return m.containsValue(entry.getValue());
    }

    protected void remove(long time) {
        Map.Entry<Range<Long>, Segment> entry = sources.getEntry(time);
        if (entry != null) {
            sources.remove(entry.getKey());
        }
        cache = null;
        lengthChanged = true;
    }
    protected void remove(Range<Long> range){
        sources.remove(range);
        lengthChanged = true;
        cache = null;
    }
    protected void resize(Map.Entry<Range<Long>, Segment> e , long start, long duration){
        remove(e.getKey());
        add(e.getValue(),start,duration);
    }
    public Map.@Nullable Entry<Range<Long>, Segment> getEntry(long time){
        if (cache == null || !cache.getKey().contains(time)) {
            cache = sources.getEntry(time);
        }
        return cache;
    }
    public long getLength(){
        if(lengthChanged){
            if(sources.asMapOfRanges().isEmpty()){
                length=0;
            }else {
                length = sources.span().upperEndpoint();
            }
            lengthChanged = false;
        }
        return length;
    }
    public RangeMap<Long, Segment> getSources() {
        return sources;
    }
    private void writeObject(ObjectOutputStream oos) throws IOException{
        Map<Range<Long>, Segment> ranges = sources.asMapOfRanges();
        serializationRanges = new long[ranges.size() * 2];
        serializationSources = new ArrayList<>(ranges.values());
        int i=0;
        for(Range<Long> r : ranges.keySet()){
            serializationRanges[i]=r.lowerEndpoint();
            serializationRanges[i+1]=r.upperEndpoint();
            i+=2;
        }
        oos.defaultWriteObject();
        serializationRanges=null;
        serializationSources=null;
    }
    private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
        ois.defaultReadObject();
        sources=TreeRangeMap.create();
        for(int i=0;i<serializationRanges.length;i+=2){
            sources.put(Range.closedOpen(serializationRanges[i],serializationRanges[i+1]),serializationSources.get(i/2));
        }
        serializationRanges=null;
        serializationSources=null;
    }
}
