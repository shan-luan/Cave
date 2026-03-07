package com.lomekwi.cave.timeline;

import com.google.common.collect.Range;
import com.google.common.collect.RangeMap;
import com.google.common.collect.TreeRangeMap;
import com.lomekwi.cave.pipeline.Product;
import com.lomekwi.cave.pipeline.Source;

import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Track implements Serializable {
    private transient RangeMap<@NonNull Long, @NonNull Source<?>> sources = TreeRangeMap.create();
    private transient Map.Entry<Range<@NonNull Long>, @NonNull Source<?>> cache;
    private long length;
    private boolean lengthChanged = true;
    private long[] serializationRanges;
    private List<Source<?>> serializationSources;
    private static final long serialVersionUID = 1L;
    public Track() {
    }

    public void add(Source<?> src, long start, long duration) {
        if(src==null){
            sources.remove(Range.closedOpen(start,start+ duration));
        }else {
            sources.put(Range.closedOpen(start, start + duration), src);
        }
        cache = null;
        lengthChanged = true;
    }

    public Product get(long time) {
        if (cache == null || !cache.getKey().contains(time)) {
            cache = sources.getEntry(time);
        }
        if(cache==null){
            return null;
        }
        long start=cache.getKey().lowerEndpoint();
        return cache.getValue().get(time-start);
    }

    public void remove(long time) {
        Map.Entry<Range<@NonNull Long>, Source<?>> entry = sources.getEntry(time);
        if (entry != null) {
            sources.remove(entry.getKey());
        }
        cache = null;
        lengthChanged = true;
    }
    public Map.Entry<Range<@NonNull Long>, Source<?>> getEntry(long time){
        if (cache == null || !cache.getKey().contains(time)) {
            cache = sources.getEntry(time);
        }
        return cache;
    }
    public long getLength(){
        if(lengthChanged){
            long max = 0;
            for(Range<Long> range : sources.asMapOfRanges().keySet()){
                long end = range.upperEndpoint();
                if(end > max) max = end;
            }
            length = max;
            lengthChanged = false;
        }
        return length;
    }
    public RangeMap<@NonNull Long, @NonNull Source<?>> getSources() {
        return sources;
    }
    private void writeObject(ObjectOutputStream oos) throws IOException{
        Map<Range<Long>, Source<?>> ranges = sources.asMapOfRanges();
        serializationRanges=new long[ranges.size()*2];
        serializationSources=new ArrayList<>(ranges.values());
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
