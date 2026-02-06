package com.lomekwi.cine;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.utils.ScreenUtils;
import com.lomekwi.cine.content.Clip;
import com.lomekwi.cine.content.Element;
import com.lomekwi.cine.project.Project;
import com.lomekwi.cine.resource.Video;
import com.lomekwi.cine.timeline.Segment;
import com.lomekwi.cine.ui.Root;

/** {@link com.badlogic.gdx.ApplicationListener} implementation shared by all platforms. */
public class Main extends ApplicationAdapter {
    private Root ui;
    private Project project=new Project();
    @Override
    public void create() {
        GlobalVars.setProject(project);

        ui=new Root(this);
        ui.create();
        project.getTimeline().add();
        Video testVideoFile =new Video("C:\\Users\\Administrator\\Desktop\\misc\\mkv\\Test.mkv");
        Video testVideoFile2 =new Video("C:\\Users\\Administrator\\Desktop\\misc\\mp4\\oceans.mp4");
        project.getTimeline().getTrack(0).add(new Segment<Clip<Video>>(new Clip<>(testVideoFile,0),0,10_000_000));
        project.getTimeline().getTrack(0).add(new Segment<Clip<Video>>(new Clip<>(testVideoFile2,10_000_000),10_000_000,20_000_000));


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
