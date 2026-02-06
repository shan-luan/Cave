package com.lomekwi.cine;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.utils.ScreenUtils;
import com.lomekwi.cine.content.Clip;
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
        //测试
        Video testVideoFile =new Video("C:\\Users\\Administrator\\Desktop\\misc\\mp4\\Oceans.mp4");
        Clip<Video> clip1 =new Clip<>(testVideoFile, 10_000_000);
        Clip<Video> clip2=new Clip<>(testVideoFile, 20_000_000);
        project.getTimeline().getTrack(0).add(new Segment<>(clip1, 0, 1_000_000));
        project.getTimeline().getTrack(0).add(new Segment<>(clip2,1_000_001, 1_000_000));
        project.getTimeline().getTrack(0).add(new Segment<>(clip1, 3_000_001, 1_000_000));
        project.getTimeline().getTrack(0).add(new Segment<>(clip2,4_000_001, 1_000_000));
        project.getTimeline().getTrack(0).add(new Segment<>(clip1, 5_000_001, 1_000_000));
        project.getTimeline().getTrack(0).add(new Segment<>(clip2,6_000_001, 1_000_000));
        project.getTimeline().getTrack(0).add(new Segment<>(clip1, 7_000_001, 1_000_000));
        project.getTimeline().getTrack(0).add(new Segment<>(clip2,8_000_001, 1_000_000));
        project.getTimeline().getTrack(0).add(new Segment<>(clip1, 9_000_001, 1_000_000));
        project.getTimeline().getTrack(0).add(new Segment<>(clip2,10_000_001, 1_000_000));


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
    //测试用
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
