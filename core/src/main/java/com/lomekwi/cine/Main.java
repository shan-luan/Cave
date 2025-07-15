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
        project.getTimeline().getTrack(0).add(new VideoClip(new Video("C:\\Users\\Administrator\\Desktop\\misc\\mp4\\oceans.mp4"),0,5_000_000,2_000_000));
        project.getTimeline().getTrack(0).add(new VideoClip(new Video("C:\\Users\\Administrator\\Desktop\\misc\\mkv\\Test.mkv"),0,5_000_000, 0));
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
