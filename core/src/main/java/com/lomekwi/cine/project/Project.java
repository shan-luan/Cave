package com.lomekwi.cine.project;

import com.lomekwi.cine.timeline.Timeline;
import com.lomekwi.cine.timeline.playback.PlayController;

public class Project {
    private final Timeline timeline=new Timeline();
    private final PlayController playController=new PlayController(timeline);
    public Timeline getTimeline(){
        return timeline;
    }
    public PlayController getPlayController(){
        return playController;
    }
}
