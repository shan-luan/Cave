package com.lomekwi.cave.task;

import static com.lomekwi.cave.util.Units.SECOND;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.Matrix4;
import com.lomekwi.cave.pipeline.Frame;
import com.lomekwi.cave.pipeline.image.ImgFrame;
import com.lomekwi.cave.pipeline.image.Transform;
import com.lomekwi.cave.timeline.Timeline;

import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.atomic.AtomicReferenceArray;
//TODO:关闭资源
public class VideoExportTask implements Task{
    private final Timeline timeline;
    private final FFmpegFrameRecorder recorder;
    private final float xOffset;
    private final float yOffset;
    private final AtomicReferenceArray<Frame> frames;
    private final FrameBuffer fb;
    private final SpriteBatch batch;
    private volatile long t=0;
    private final long frameLen;
    private final org.bytedeco.javacv.Frame cvFrame;
    private final SynchronousQueue<org.bytedeco.javacv.Frame> queue=new SynchronousQueue<>();
    private final Matrix4 projMatrix;
    public VideoExportTask(@NonNull Timeline timeline, File outputFile, int width, int height, double fps, float xOffset, float yOffset) {
        this.timeline = timeline;
        recorder=new FFmpegFrameRecorder(outputFile,width, height);
        this.xOffset=xOffset;
        this.yOffset=yOffset;
        int i=0;
        for(var ignored : timeline){
            i++;
        }
        frames= new AtomicReferenceArray<>(i);
        fb=new FrameBuffer(Pixmap.Format.RGBA8888,width,height,true);
        batch=new SpriteBatch();
        frameLen= (long) (SECOND/fps);
        cvFrame=new org.bytedeco.javacv.Frame(width,height,org.bytedeco.javacv.Frame.DEPTH_UBYTE,4);
        projMatrix=new Matrix4().setToOrtho(0, fb.getWidth(), fb.getHeight(), 0, 0, 1);
    }

    @Override
    public float getProgress() {
        return (float) t /timeline.getLength();
    }

    @Override
    public void run() {
        try(recorder){
            recorder.start();
            int i;
            while (t<timeline.getLength()){
                i=0;
                for(var track : timeline){
                    frames.set(i,track.get(t));
                    i++;
                }
                Gdx.app.postRunnable(this::mixVideoFrame);
                recorder.record(queue.take());
                System.out.println(t);
                t+=frameLen;
            }
            recorder.stop();
        }catch (Exception e){
            throw new RuntimeException(e);
        }
    }
    private void mixVideoFrame() {
        fb.begin();

        batch.begin();
        batch.setProjectionMatrix(projMatrix);
        Gdx.gl.glClearColor(0,0,0,1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        for(int i=0;i<frames.length();i++){
            Frame frame=frames.get(i);
            if(frame instanceof ImgFrame f){
                Transform t=f.getTransform();
                f.update();
                batch.draw(f.getTexture(),t.x+xOffset,t.y+yOffset,t.width,t.height);
            }
        }
        batch.end();

        Gdx.gl.glReadPixels(0,0,fb.getWidth(),fb.getHeight(), GL20.GL_RGBA,GL20.GL_UNSIGNED_BYTE, cvFrame.image[0]);

        fb.end();
        cvFrame.timestamp=t;
        try {
            queue.put(cvFrame);
        } catch (InterruptedException e) {
            // ignore
        }
    }
}
