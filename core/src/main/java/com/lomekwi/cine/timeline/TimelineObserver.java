package com.lomekwi.cine.timeline;

import com.lomekwi.cine.content.Element;

public interface TimelineObserver {
    void onTrackAdded(Track track);
    void onTrackRemoved(Track track);
    void onElementAdded(Track track,Element element);
    void onElementRemoved(Track track,Element element);
}
