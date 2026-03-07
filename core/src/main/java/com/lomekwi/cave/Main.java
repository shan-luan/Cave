package com.lomekwi.cave;

import static com.lomekwi.cave.util.Units.HOUR;
import static com.lomekwi.cave.util.Units.SECOND;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.lomekwi.cave.element.FilteredSrc;
import com.lomekwi.cave.pipeline.image.ImgProd;
import com.lomekwi.cave.pipeline.image.TransFilter;
import com.lomekwi.cave.pipeline.image.VdoClipSrc;
import com.lomekwi.cave.project.Project;
import com.lomekwi.cave.resource.media.VdoRes;
import com.lomekwi.cave.ui.Root;

import org.bytedeco.ffmpeg.avcodec.AVCodec;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.Loader;
import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacpp.PointerPointer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;


/** {@link com.badlogic.gdx.ApplicationListener} implementation shared by all platforms. */
public class Main extends ApplicationAdapter {
    private Root ui;
    private Project project=new Project();
    @Override
    public void create() {
        Loader.load(org.bytedeco.ffmpeg.global.avcodec.class);

        PointerPointer<Pointer> opaque = new PointerPointer<>(1).put((Pointer) null);

        AVCodec codec;
        System.out.println("可用的codec：");
        while ((codec = avcodec.av_codec_iterate(opaque)) != null) {
            System.out.print(codec.name().getString()+"|");
        }
        System.out.println("没了");

        GlobalVars.setProject(project);
        ui = new Root(this);
        ui.create();
        project.timeline.addTrack().addTrack();

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
        FilteredSrc<ImgProd> clip=new FilteredSrc<>(new VdoClipSrc(testVideoFile,0),new TransFilter(0,0,1,1,0));
        project.timeline.add(0,clip,0,100*SECOND);
        project.timeline.add(1,clip,100*SECOND,200*SECOND);
        System.out.println(project.timeline.getLength());
        project.playhead.setPlaying(true);
    }

    @Override
    public void render() {
        project.playhead.update();
        project.distributor.distribute(project.playhead.getTime());
        ui.render();
    }

    @Override
    public void dispose() {
        ui.dispose();
    }
    //测试用
    @Override
    public void resize(int width, int height) {
        ui.resize(width, height);
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
