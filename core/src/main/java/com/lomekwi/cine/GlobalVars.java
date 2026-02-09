package com.lomekwi.cine;

import com.google.common.collect.Range;
import com.lomekwi.cine.content.Element;
import com.lomekwi.cine.project.Project;
import com.lomekwi.cine.timeline.Track;
import com.lomekwi.cine.timeline.playback.PlayController;
import com.lomekwi.cine.timeline.playback.Playhead;

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
    public static PlayController getPlayController() {
        return project.getPlayController();
    }
    public static Playhead getPlayhead() {
        return project.getPlayController().getPlayhead();
    }
    public static long getCurrentTime() {
        return getPlayhead().getTime();
    }
    public static Range<@NonNull Long> getCurrentElementRangeIn(Track track){
        Map.Entry<Range<@NonNull Long>, Element> entry = track.getEntry(getCurrentTime());
        if (entry != null) {
            return entry.getKey();
        }else {
            return Range.atLeast(0L);//FIXME:当为Gap时无法获得当前区间
        }
    }
}
