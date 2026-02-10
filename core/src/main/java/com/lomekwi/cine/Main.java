package com.lomekwi.cine;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.ScreenUtils;
import com.lomekwi.cine.content.Clip;
import com.lomekwi.cine.project.Project;
import com.lomekwi.cine.resource.media.VdoRes;
import com.lomekwi.cine.ui.Root;

import static com.lomekwi.cine.util.Units.*;

import org.jspecify.annotations.NonNull;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.stream.IntStream;


/** {@link com.badlogic.gdx.ApplicationListener} implementation shared by all platforms. */
public class Main extends ApplicationAdapter {
    private Root ui;
    private Project project=new Project();
    @Override
    public void create() {
        GlobalVars.setProject(project);
        ui = new Root(this);
        ui.create();
        project.getTimeline().add().add();

        // blame Android
        FileHandle handle = Gdx.files.internal("test.mp4");
        String videoPath = null;
        try {
            if (Gdx.app.getType() == Application.ApplicationType.Android) {
                File tmpFile = File.createTempFile("test", ".mp4");
                tmpFile.deleteOnExit();
                try (InputStream is = handle.read(); FileOutputStream os = new FileOutputStream(tmpFile)) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = is.read(buffer)) != -1) {
                        os.write(buffer, 0, read);
                    }
                }
                videoPath = tmpFile.getAbsolutePath();
            } else {
                videoPath = handle.path();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        VdoRes testVideoFile;
        try {
            testVideoFile = new VdoRes(videoPath);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }

        project.getPlayController().start();
    }

    @Override
    public void render() {
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
