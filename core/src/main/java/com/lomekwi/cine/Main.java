package com.lomekwi.cine;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.utils.ScreenUtils;
import com.lomekwi.cine.content.Clip;
import com.lomekwi.cine.project.Project;
import com.lomekwi.cine.resource.Video;
import com.lomekwi.cine.timeline.Seg;
import com.lomekwi.cine.ui.Root;

import static com.lomekwi.cine.util.Units.*;

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
        Video testVideoFile =new Video("C:\\Users\\Administrator\\Desktop\\168885122-1-192.mp4");
        Clip<Video> clip1 =new Clip<>(testVideoFile, 10*SECOND);
        Clip<Video> clip2=new Clip<>(testVideoFile, 20*SECOND);
        project.getTimeline().getTrack(0).add(new Seg(clip1, 0, 1*SECOND));
        project.getTimeline().getTrack(0).add(new Seg(clip2,1*SECOND, 2*SECOND));
        project.getTimeline().getTrack(0).add(new Seg(clip1, 3*SECOND,1*SECOND));
        project.getTimeline().getTrack(0).add(new Seg(clip2,4*SECOND, 1*SECOND));
        project.getTimeline().getTrack(0).add(new Seg(clip1, 5*SECOND,1*SECOND));
        project.getTimeline().getTrack(0).add(new Seg(clip2,6*SECOND, 1*SECOND));
        project.getTimeline().getTrack(0).add(new Seg(clip1, 7*SECOND,1*SECOND));
        project.getTimeline().getTrack(0).add(new Seg(clip2,8*SECOND, 1*SECOND));
        project.getTimeline().getTrack(0).add(new Seg(clip1, 9*SECOND,1*SECOND));
        project.getTimeline().getTrack(0).add(new Seg(clip2,10*SECOND,1*SECOND));


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
