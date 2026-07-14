package com.lomekwi.cave.ui.editpanel.tlarea;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.google.common.collect.Range;
import com.kotcrab.vis.ui.widget.MenuItem;
import com.kotcrab.vis.ui.widget.PopupMenu;
import com.lomekwi.cave.app.App;
import com.lomekwi.cave.project.Project;
import com.lomekwi.cave.resource.media.MediaFactory;
import com.lomekwi.cave.timeline.Segment;
import com.lomekwi.cave.timeline.SegmentGroup;
import com.lomekwi.cave.timeline.TextSeg;
import com.lomekwi.cave.timeline.UndoManager;
import com.lomekwi.cave.ui.listeners.ChangeListenerX;
import com.lomekwi.cave.util.MimeType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import games.spooky.gdx.nativefilechooser.NativeFileChooserCallback;
import games.spooky.gdx.nativefilechooser.NativeFileChooserConfiguration;
import games.spooky.gdx.nativefilechooser.NativeFileChooserIntent;

public class TlGroupMenu extends PopupMenu {
    private final TlGroup tlGroup;
    private long time;

    TlGroupMenu(TlGroup tlGroup) {
        this.tlGroup = tlGroup;
        PopupMenu addMenu = new PopupMenu();
        addMenu.addItem(new MenuItem("媒体片段", new ChangeListenerX(this::onAddMedia)));
        addMenu.addItem(new MenuItem("文本片段", new ChangeListenerX(this::onAddText)));
        MenuItem addItem = new MenuItem("新增...");
        addItem.setSubMenu(addMenu);
        addItem(addItem);
    }

    public void setContext(long time) {
        this.time = time;
    }

    private void onAddMedia() {
        NativeFileChooserConfiguration conf = new NativeFileChooserConfiguration();
        conf.title = "选择媒体文件";
        conf.intent = NativeFileChooserIntent.OPEN;
        if (Gdx.app.getType() == Application.ApplicationType.Android) {
            conf.mimeFilter = "*/*";
        } else {
            conf.nameFilter = (dir, name) -> {
                String mime = MimeType.detectMimeType(new File(dir, name));
                return mime != null && MediaFactory.isSupported(mime);
            };
        }
        App.fileChooser.chooseFile(conf, new NativeFileChooserCallback() {
            @Override
            public void onFileChosen(FileHandle file) {
                addMediaFile(file.file());
            }

            @Override
            public void onCancellation() {}

            @Override
            public void onError(Exception exception) {
                Gdx.app.error("TlGroupMenu", "选择文件失败", exception);
            }
        });
    }

    private void onAddText() {
        TextSeg seg = new TextSeg();
        seg.setOrigin(time);
        long duration = seg.getDuration();
        if (duration <= 0) return;

        int targetTrack = 0;
        var range = Range.closedOpen(time, time + duration);
        while (!tlGroup.getTimeline().getTrack(targetTrack).isFree(range, Set.of())) {
            targetTrack++;
        }

        tlGroup.getTimeline().add(tlGroup.getTimeline().getTrack(targetTrack), seg, time, duration);
        Project project = tlGroup.getProject();
        project.undoManager.record(new UndoManager.AddSegCommand(
            tlGroup.getTimeline().getTrack(targetTrack), seg, time, duration));

        tlGroup.markTimelineDirty();
    }

    private void addMediaFile(File file) {
        Project project = tlGroup.getProject();
        try {
            List<Segment> segments = project.mediaSegFactory.getAll(file);
            if (segments.isEmpty()) return;

            int baseTrack = 0;
            int trackOffset = 0;
            var cmds = new ArrayList<UndoManager.UndoableCommand>();
            List<Segment> added = new ArrayList<>();

            for (Segment seg : segments) {
                seg.setOrigin(time);
                long duration = seg.getDuration();
                if (duration <= 0) continue;

                int targetTrack = baseTrack + trackOffset;
                var range = Range.closedOpen(time, time + duration);
                while (!tlGroup.getTimeline().getTrack(targetTrack).isFree(range, Set.of())) {
                    targetTrack++;
                }

                tlGroup.getTimeline().add(tlGroup.getTimeline().getTrack(targetTrack), seg, time, duration);
                cmds.add(new UndoManager.AddSegCommand(tlGroup.getTimeline().getTrack(targetTrack), seg, time, duration));
                trackOffset = targetTrack - baseTrack + 1;
                added.add(seg);
            }

            if (!cmds.isEmpty()) {
                project.undoManager.record(new UndoManager.CompoundCommand(cmds.toArray(new UndoManager.UndoableCommand[0])));
            }

            if (added.size() >= 2) {
                SegmentGroup group = new SegmentGroup();
                for (Segment seg : added) {
                    group.add(seg);
                }
            }

            tlGroup.markTimelineDirty();
        } catch (IOException e) {
            Gdx.app.error("TlGroupMenu", "添加媒体片段失败: " + e.getMessage());
        }
    }
}
