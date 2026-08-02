package com.lomekwi.cave.ui.editpanel.tlarea;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop;

import com.google.common.collect.Range;

import com.lomekwi.cave.app.App;
import com.lomekwi.cave.util.MimeType;
import com.lomekwi.cave.timeline.Segment;
import com.lomekwi.cave.timeline.SegmentGroup;
import com.lomekwi.cave.timeline.Track;
import com.lomekwi.cave.timeline.UndoManager;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class TlGroupDropTarget extends DragAndDrop.Target {

    private final TlGroup tlGroup;

    public TlGroupDropTarget(TlGroup tlGroup) {
        super(tlGroup);
        this.tlGroup = tlGroup;
    }

    @Override
    public boolean drag(DragAndDrop.Source source, DragAndDrop.Payload payload, float x, float y, int pointer) {
        if (!(payload.getObject() instanceof File file)) {
            return false;
        }
        String mimeType = MimeType.detectMimeType(file);
        return App.mediaFactory.isSupported(mimeType);
    }

    @Override
    public void drop(DragAndDrop.Source source, DragAndDrop.Payload payload, float x, float y, int pointer) {
        try {
            File file = (File) payload.getObject();
            List<Segment> segments = tlGroup.project.mediaSegFactory.getAll(file);
            long startTime = tlGroup.xToAbsoluteTime(x);
            int baseTrack = tlGroup.yToTrackIndex(y);
            int trackOffset = 0;
            var cmds = new ArrayList<UndoManager.UndoableCommand>();
            List<Segment> added = new ArrayList<>();
            for (Segment seg : segments) {
                seg.setOrigin(startTime);
                long duration = seg.getDuration();
                if (duration <= 0) continue;
                int targetTrack = baseTrack + trackOffset;
                var range = Range.closedOpen(startTime, startTime + duration);
                while (!tlGroup.timeline.getTrack(targetTrack).isFree(range, Set.of())) {
                    targetTrack++;
                }
                tlGroup.timeline.add(tlGroup.timeline.getTrack(targetTrack), seg, startTime, duration);
                cmds.add(new UndoManager.AddSegCommand(tlGroup.timeline.getTrack(targetTrack), seg, startTime, duration));
                trackOffset = targetTrack - baseTrack + 1;
                added.add(seg);
            }
            if (!cmds.isEmpty()) {
                tlGroup.project.undoManager.record(new UndoManager.CompoundCommand(cmds.toArray(new UndoManager.UndoableCommand[0])));
            }
            if (added.size() >= 2) {
                SegmentGroup group = new SegmentGroup();
                for (Segment seg : added) {
                    group.add(seg);
                }
            }
            tlGroup.dirty = true;
        } catch (IOException e) {
            Gdx.app.error("TlGroup", "拖拽文件失败: " + e.getMessage());
        }
    }
}