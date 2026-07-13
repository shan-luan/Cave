package com.lomekwi.cave.task;

import static com.lomekwi.cave.util.Units.SECOND;
import static com.lomekwi.cave.util.i18n.I18N.i18n;
import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_AAC;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.Matrix4;
import com.lomekwi.cave.pipeline.Frame;
import com.lomekwi.cave.pipeline.audio.AudFrame;
import com.lomekwi.cave.pipeline.image.ImgFrame;
import com.lomekwi.cave.pipeline.image.Transform;
import com.lomekwi.cave.resource.decoder.AudDecRes;
import com.lomekwi.cave.timeline.AudSeg;
import com.lomekwi.cave.timeline.Segment;
import com.lomekwi.cave.timeline.Timeline;
import com.lomekwi.cave.timeline.Track;

import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.FrameRecorder;
import org.jspecify.annotations.NonNull;

import java.io.File;
import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.atomic.AtomicReferenceArray;
public class VideoExportTask implements Task{
    private static final int AUDIO_FRAME_SIZE = AudDecRes.FRAME_SIZE;
    private static final int AUDIO_SAMPLE_RATE = 44100;
    private static final int AUDIO_CHANNELS = 2;
    private static final long AUDIO_FRAME_DURATION = AUDIO_FRAME_SIZE * SECOND / AUDIO_SAMPLE_RATE / AUDIO_CHANNELS;

    private final Timeline timeline;
    private final FFmpegFrameRecorder recorder;
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
    public VideoExportTask(@NonNull Timeline timeline, File outputFile, int width, int height, double fps,int bitrate) {
        this.timeline = timeline;
        recorder=new FFmpegFrameRecorder(outputFile,width, height);
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
            recorder.setAudioChannels(AUDIO_CHANNELS);
            recorder.setSampleRate(AUDIO_SAMPLE_RATE);
            recorder.setAudioCodec(AV_CODEC_ID_AAC);
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
            exportAudio();
            recorder.stop();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void exportAudio() throws Exception {
        Track[] tracks = timeline.getTracks().toArray(new Track[0]);
        AudSeg[] active = new AudSeg[tracks.length];
        float[] mixBuf = new float[AUDIO_FRAME_SIZE];

        for (long audioT = 0; audioT < timeline.getLength(); audioT += AUDIO_FRAME_DURATION) {
            Arrays.fill(mixBuf, 0f);

            for (int i = 0; i < tracks.length; i++) {
                if (tracks[i].getLength() == 0) continue;

                AudSeg seg = active[i];
                if (seg == null || !seg.getRange().contains(audioT)) {
                    var entry = tracks[i].getEntry(audioT);
                    seg = null;
                    if (entry != null && entry.getValue() instanceof AudSeg s) {
                        s.sync(audioT);
                        seg = s;
                    }
                    active[i] = seg;
                }
                if (seg == null) continue;

                var frame = seg.get(audioT);
                if (frame instanceof AudFrame af && af.getSamples() != null) {
                    float[] samples = af.getSamples();
                    int len = Math.min(samples.length, mixBuf.length);
                    for (int si = 0; si < len; si++) {
                        mixBuf[si] += samples[si];
                    }
                }
            }

            for (int i = 0; i < mixBuf.length; i++) {
                mixBuf[i] = Math.max(-1.0f, Math.min(1.0f, mixBuf[i]));
            }

            recorder.recordSamples(FloatBuffer.wrap(mixBuf));
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
                f.render(batch);
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
