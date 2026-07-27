package com.lomekwi.cave.ui.editpanel.tlarea;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;

import com.google.common.collect.Range;

import com.lomekwi.cave.app.App;
import com.lomekwi.cave.project.Project;
import com.lomekwi.cave.timeline.Segment;
import com.lomekwi.cave.timeline.SegmentSelectedEvent;
import com.lomekwi.cave.timeline.SegmentSet;
import com.lomekwi.cave.timeline.SegmentSetSelectedEvent;
import com.lomekwi.cave.timeline.Track;
import com.lomekwi.cave.timeline.Timeline;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TlGroupCaptureListener extends InputListener {

    private final TlGroup tlGroup;

    public TlGroupCaptureListener(TlGroup tlGroup) {
        this.tlGroup = tlGroup;
    }

    @Override
    public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
        if (button != Input.Buttons.LEFT) return false;

        if (App.shortcutManager.isActive(TlGroup.Actions.MARQUEE_SELECT)) {
            tlGroup.marqueeActive = true;
            tlGroup.marqueeStartX = x;
            tlGroup.marqueeStartY = y;
            tlGroup.marqueeEndX = x;
            tlGroup.marqueeEndY = y;
            return true;
        }

        int trackIndex = tlGroup.yToTrackIndex(y);
        boolean onSegment = trackIndex >= 0 && trackIndex < tlGroup.timeline.getTracks().size()
            && tlGroup.timeline.getTrack(trackIndex).getEntry(tlGroup.xToAbsoluteTime(x)) != null;
        if (!onSegment) {
            tlGroup.playhead.seek(Math.max(tlGroup.xToAbsoluteTime(x), 0));
        }
        return false;
    }

    @Override
    public void touchDragged(InputEvent event, float x, float y, int pointer) {
        if (!tlGroup.marqueeActive) return;
        tlGroup.marqueeEndX = x;
        tlGroup.marqueeEndY = y;
        event.stop();
    }

    @Override
    public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
        if (!tlGroup.marqueeActive) return;
        tlGroup.marqueeActive = false;
        event.stop();

        float minX = Math.min(tlGroup.marqueeStartX, tlGroup.marqueeEndX);
        float maxX = Math.max(tlGroup.marqueeStartX, tlGroup.marqueeEndX);
        float minY = Math.min(tlGroup.marqueeStartY, tlGroup.marqueeEndY);
        float maxY = Math.max(tlGroup.marqueeStartY, tlGroup.marqueeEndY);

        if (maxX - minX < 2 || maxY - minY < 2) return;

        int firstTrack = Math.max(0, tlGroup.yToTrackIndex(maxY));
        int lastTrack = Math.min(tlGroup.timeline.getTracks().size() - 1, tlGroup.yToTrackIndex(minY));

        Set<Segment> toSelect = new HashSet<>();
        for (int i = firstTrack; i <= lastTrack; i++) {
            Track track = tlGroup.timeline.getTrack(i);
            float trackTop = tlGroup.trackIndexToTopY(i);
            float trackBottom = trackTop - tlGroup.view.trackHeight;

            if (trackTop <= minY || trackBottom >= maxY) continue;

            long segStartTime = tlGroup.xToAbsoluteTime(minX);
            long segEndTime = tlGroup.xToAbsoluteTime(maxX);
            if (segStartTime > segEndTime) {
                long t = segStartTime;
                segStartTime = segEndTime;
                segEndTime = t;
            }

            Range<Long> timeRange = Range.closedOpen(segStartTime, segEndTime);
            for (var entry : track.getSubRangeMapAsEntrySet(timeRange)) {
                Segment seg = entry.getValue();
                float segLeft = tlGroup.absoluteTimeToX(seg.getRange().lowerEndpoint());
                float segRight = tlGroup.absoluteTimeToX(seg.getRange().upperEndpoint());

                if (segRight > minX && segLeft < maxX) {
                    if (seg.getGroup() != null) {
                        toSelect.addAll(seg.getGroup().getSegments());
                    } else {
                        toSelect.add(seg);
                    }
                }
            }
        }

        if (!toSelect.isEmpty()) {
            tlGroup.clearSelection();
            for (Segment seg : toSelect) {
                tlGroup.selectedSegments.add(seg);
                seg.setSelected(true);
            }
            int count = tlGroup.selectedSegments.size();
            if (count >= 2) {
                var e = new SegmentSelectedEvent(null, null, count);
                tlGroup.project.projEventBus.post(e);
                App.appEventBus.post(e);
                var ge = new SegmentSetSelectedEvent(tlGroup.selectedSegments, count);
                tlGroup.project.projEventBus.post(ge);
                App.appEventBus.post(ge);
            } else if (count == 1) {
                Segment seg = toSelect.iterator().next();
                var e = new SegmentSelectedEvent(seg, seg.getTrack(), 1);
                tlGroup.project.projEventBus.post(e);
                App.appEventBus.post(e);
            }
        }
    }
}