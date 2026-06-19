package com.lomekwi.cave.timeline;


import com.lomekwi.cave.project.Project;
import com.lomekwi.cave.resource.Resource;
import com.lomekwi.cave.resource.media.AudRes;
import com.lomekwi.cave.resource.media.MediaCreatedEvent;
import com.lomekwi.cave.resource.media.MediaFactory;
import com.lomekwi.cave.resource.media.MedRes;
import com.lomekwi.cave.resource.media.VdoRes;
import com.lomekwi.cave.util.MimeType;

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class SegFactory implements Serializable {
    private Project project;
    private transient Map<Class<? extends Resource>, Function<? extends Resource,Segment>> map;
    @Serial
    private static final long serialVersionUID = 1L;
    public SegFactory(Project project){
        this.project = project;
        this.map = new HashMap<>();
        initDefaultMappings();
    }

    private void initDefaultMappings() {
        register(VdoRes.class, source -> new VdoSeg((VdoRes) source));
        register(AudRes.class, source -> new AudSeg((AudRes) source));
    }
    public void register(Class<? extends Resource> clazz, Function<? extends Resource,Segment> constructor){
        map.put(clazz,constructor);
    }
    public void unregister(Class<? extends Resource> clazz){
        map.remove(clazz);
    }

    /**
     * 获取文件对应的所有片段。
     * 对于同时包含视频和音频流的文件，可能返回多个 Segment。
     */
    public List<Segment> getAll(File file) throws IOException {
        Collection<Resource> existing = project.resources.get(file);

        if (existing.isEmpty()) {
            String mimeType = MimeType.detectMimeType(file);
            if (mimeType == null) {
                throw new IOException("无法检测文件MIME类型: " + file.getName());
            }

            for (MedRes medRes : MediaFactory.createAll(mimeType, file.getPath())) {
                project.resources.put(file, medRes);
                project.projEventBus.post(new MediaCreatedEvent(file, medRes));
            }
            existing = project.resources.get(file);
        }

        List<Segment> segments = new ArrayList<>();
        for (Resource resource : existing) {
            segments.add(applyUnchecked(map.get(resource.getClass()), resource));
        }
        return segments;
    }

    /**
     * 获取文件对应的第一个主要片段（兼容单片段场景）。
     */
    public Segment get(File file) throws IOException {
        return getAll(file).get(0);
    }
    @SuppressWarnings("unchecked")
    private <R extends Resource> Segment applyUnchecked(Function<? extends Resource, Segment> fn, R resource) {
        return ((Function<R, Segment>) fn).apply(resource);
    }

    @Serial
    private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
        ois.defaultReadObject();
        this.map = new HashMap<>();
        initDefaultMappings();
    }
}
