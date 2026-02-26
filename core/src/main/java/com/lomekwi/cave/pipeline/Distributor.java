package com.lomekwi.cave.pipeline;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.lomekwi.cave.timeline.Timeline;

import java.util.ArrayList;

public class Distributor {
    private final Timeline timeline;
    private final ArrayList<Product> collector=new ArrayList<>();
    private final Multimap<Class<?>, Sink<?>> sinks=HashMultimap.create();
    public Distributor(Timeline timeline) {
        this.timeline = timeline;
    }
    @SuppressWarnings("unchecked")
    public void distribute(long time) {
        collector.clear();
        timeline.getActiveElements(time, collector);

        for (Product product : collector) {
            Class<?> type = product.getClass();
            for (Sink<?> sink : sinks.get(type)) {
                ((Sink<Product>) sink).sink(product);
            }
        }
    }
    public <T extends Product> void registerSink(Class<T> type, Sink<T> sink) {
        sinks.put(type, sink);
    }
    public <T extends Product> void unregisterSink(Class<T> type, Sink<T> sink) {
        sinks.remove(type, sink);
    }
    public void unregisterSink(Sink<?> sink){
        sinks.entries().removeIf(classSinkEntry -> classSinkEntry.getValue().equals(sink));
    }
}
