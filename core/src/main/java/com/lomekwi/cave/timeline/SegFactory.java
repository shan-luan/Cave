package com.lomekwi.cave.timeline;


import com.lomekwi.cave.project.Project;
import com.lomekwi.cave.resource.Resource;
import com.lomekwi.cave.resource.media.Media;
import com.lomekwi.cave.resource.media.VdoRes;

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class SegFactory implements Serializable {
    private Project project;
    private transient Map<Class<? extends Resource>, Function<? extends Resource,Segment>> map;
    private static final long serialVersionUID = 1L;
    public SegFactory(Project project){
        this.project = project;
        this.map = new HashMap<>();
        initDefaultMappings();
    }
    
    private void initDefaultMappings() {
        register(VdoRes.class, source -> new VdoSeg((VdoRes) source));
    }
    public void register(Class<? extends Resource> clazz, Function<? extends Resource,Segment> constructor){
        map.put(clazz,constructor);
    }
    public void unregister(Class<? extends Resource> clazz){
        map.remove(clazz);
    }
    public Segment get(File file) throws IOException {
        Resource resource = project.resources.get(file);
        if(resource == null){
            resource = Media.create(Files.probeContentType(file.toPath()),file.getPath());
        }
        return applyUnchecked(map.get(resource.getClass()), resource);
    }
    @SuppressWarnings("unchecked")
    private <R extends Resource> Segment applyUnchecked(Function<? extends Resource, Segment> fn, R resource) {
        return ((Function<R, Segment>) fn).apply(resource);
    }
    
    private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
        ois.defaultReadObject();
        this.map = new HashMap<>();
        initDefaultMappings();
    }
}
