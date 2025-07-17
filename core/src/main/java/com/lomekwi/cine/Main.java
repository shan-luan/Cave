package com.lomekwi.cine;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.utils.ScreenUtils;
import com.lomekwi.cine.content.VideoClip;
import com.lomekwi.cine.project.Project;
import com.lomekwi.cine.resource.Video;
import com.lomekwi.cine.timeline.Track;
import com.lomekwi.cine.ui.Root;

/** {@link com.badlogic.gdx.ApplicationListener} implementation shared by all platforms. */
public class Main extends ApplicationAdapter {
    private Root ui;
    private Project project=new Project();
    @Override
    public void create() {
        ui=new Root(this);
        ui.create();
        project.getTimeline().add(new Track());
        Video testVideo=new Video("C:\\Users\\Administrator\\Desktop\\168885122-1-192.mp4");
        project.getTimeline().getTrack(0).add(new VideoClip(testVideo,0,1_000_000,0,project.getPlayController().getPlayhead()));
        project.getTimeline().getTrack(0).add(new VideoClip(testVideo,1_000_000,1_000_000,1_000_000,project.getPlayController().getPlayhead()));
        project.getTimeline().getTrack(0).add(new VideoClip(testVideo,2_000_000,1_000_000,2_000_000,project.getPlayController().getPlayhead()));
        project.getTimeline().getTrack(0).add(new VideoClip(testVideo,3_000_000,1_000_000,3_000_000,project.getPlayController().getPlayhead()));
        project.getTimeline().getTrack(0).add(new VideoClip(testVideo,4_000_000,1_000_000,4_000_000,project.getPlayController().getPlayhead()));
        project.getTimeline().getTrack(0).add(new VideoClip(testVideo,5_000_000,1_000_000,5_000_000,project.getPlayController().getPlayhead()));
        project.getTimeline().getTrack(0).add(new VideoClip(testVideo,6_000_000,1_000_000,6_000_000,project.getPlayController().getPlayhead()));
        project.getTimeline().getTrack(0).add(new VideoClip(testVideo,7_000_000,1_000_000,7_000_000,project.getPlayController().getPlayhead()));
        project.getTimeline().getTrack(0).add(new VideoClip(testVideo,8_000_000,1_000_000,8_000_000,project.getPlayController().getPlayhead()));
        project.getTimeline().getTrack(0).add(new VideoClip(testVideo,9_000_000,1_000_000,9_000_000,project.getPlayController().getPlayhead()));
        project.getTimeline().getTrack(0).add(new VideoClip(testVideo,10_000_000,1_000_000,10_000_000,project.getPlayController().getPlayhead()));

        project.getPlayController().start();
    }

    @Override
    public void render() {
        ScreenUtils.clear(0f, 0f, 0f, 1f);
        ui.render();
        project.getPlayController().update();
    }

    @Override
    public void dispose() {
        ui.dispose();
    }
    @Override
    public void resize(int width, int height) {
        ui.resize(width, height);
        project.getPlayController().seek(0);
    }
    @Override
    public void pause() {
        ui.pause();
    }
    @Override
    public void resume() {
        ui.resume();
    }
    public Project getProject()
    {
        return project;
    }
}
