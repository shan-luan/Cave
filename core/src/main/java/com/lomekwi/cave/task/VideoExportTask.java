package com.lomekwi.cave.task;

import static com.lomekwi.cave.util.Units.SECOND;
import static com.lomekwi.cave.util.i18n.I18N.i18n;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.Matrix4;
import com.lomekwi.cave.pipeline.Frame;
import com.lomekwi.cave.pipeline.image.ImgFrame;
import com.lomekwi.cave.pipeline.image.Transform;
import com.lomekwi.cave.timeline.Segment;
import com.lomekwi.cave.timeline.Timeline;

import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.FrameRecorder;
import org.jspecify.annotations.NonNull;

import java.io.File;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.atomic.AtomicReferenceArray;
public class VideoExportTask implements Task{
    private final Timeline timeline;
    private final FFmpegFrameRecorder recorder;
    private final float xOffset;
    private final float yOffset;
    private final AtomicReferenceArray<Frame> frames;
    private final Segment[] activeSegments;
    private final FrameBuffer fb;
    private final SpriteBatch batch;
    private volatile long t=0;
    private final long frameLen;
    private final org.bytedeco.javacv.Frame cvFrame;
    private final SynchronousQueue<org.bytedeco.javacv.Frame> queue=new SynchronousQueue<>();
    private final Matrix4 projMatrix;
    private final int bitrate;
    public VideoExportTask(@NonNull Timeline timeline, File outputFile, int width, int height, double fps, float xOffset, float yOffset,int bitrate) {
        this.timeline = timeline;
        recorder=new FFmpegFrameRecorder(outputFile,width, height);
        this.xOffset=xOffset;
        this.yOffset=yOffset;
        int i=0;
        for(var ignored : timeline){
            i++;
        }
        frames= new AtomicReferenceArray<>(i);
        activeSegments=new Segment[i];
        fb=new FrameBuffer(Pixmap.Format.RGBA8888,width,height,true);
        batch=new SpriteBatch();
        frameLen= (long) (SECOND/fps);
        cvFrame=new org.bytedeco.javacv.Frame(width,height,org.bytedeco.javacv.Frame.DEPTH_UBYTE,4);
        projMatrix=new Matrix4().setToOrtho(0, fb.getWidth(), fb.getHeight(), 0, 0, 1);
        this.bitrate=bitrate;
    }

    @Override
    public float getProgress() {
        return (float) t /timeline.getLength();
    }

    @Override
    public void run() {
        try {
            recorder.setVideoBitrate(bitrate);
            recorder.start();
            int i;
            while (t<timeline.getLength()){
                i=0;
                for(var track : timeline){
                    var seg = activeSegments[i];
                    if(seg==null||!seg.getRange().contains(t)){
                        var e= track.getEntry(t);
                        seg = null;
                        if(e!=null){
                            seg=e.getValue();
                            seg.sync(t);
                        }
                        activeSegments[i]=seg;
                    }
                    if (seg == null) {
                        frames.set(i, null);
                    } else {
                        frames.set(i, seg.get(t));
                    }
                    i++;
                }
                Gdx.app.postRunnable(this::mixVideoFrame);
                recorder.record(queue.take());
                t+=frameLen;
            }
            recorder.stop();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    private void mixVideoFrame() {
        fb.begin();

        batch.begin();
        batch.setProjectionMatrix(projMatrix);
        Gdx.gl.glClearColor(0,0,0,1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        for(int i=frames.length()-1;i>=0;i--){
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
    @Override
    public void close() throws FrameRecorder.Exception {
        Gdx.app.postRunnable(()->{
            fb.dispose();
            batch.dispose();
        });
        timeline.project.close();//这里的project是反序列化出来的副本，所以可以关闭而不影响用户编辑中的项目。
        cvFrame.close();
        recorder.close();
    }
    @Override
    public String getName() {
        return i18n("视频导出：")+timeline.project.name;
    }
}
