package com.lomekwi.cine;

import com.google.common.collect.Range;
import com.lomekwi.cine.pipeline.Source;
import com.lomekwi.cine.project.Project;
import com.lomekwi.cine.timeline.Track;

import org.jspecify.annotations.NonNull;

import java.util.Map;

public class GlobalVars {
    private static Project project;
    public static Project getProject() {
        return project;
    }
    protected static void setProject(Project project) {
        GlobalVars.project = project;
    }
    public static Range<@NonNull Long> getCurrentElementRangeIn(Track track){
        Map.Entry<Range<@NonNull Long>, Source<?>> entry = track.getEntry(project.playhead.getTime());
        if (entry != null) {
            return entry.getKey();
        }else {
            return Range.atLeast(0L);//FIXME:当为Gap时无法获得当前区间
        }
    }
}
