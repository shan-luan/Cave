package com.lomekwi.cave.pipeline.image;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.lomekwi.cave.pipeline.Source;
import com.lomekwi.cave.resource.media.VdoRes;
import com.lomekwi.cave.timeline.Track;

import java.io.Serial;
import java.util.concurrent.CountDownLatch;

public class VdoClipSrc extends Source<ImgFrame> {
    private VdoRes vdoRes;
    private transient Texture texture;
    private volatile transient boolean initialized;
    @Serial
    private static final long serialVersionUID = 1L;
    public VdoClipSrc(VdoRes vdoRes) {
        super();
        this.vdoRes = vdoRes;
    }

    public VdoRes getVdoRes() {
        return vdoRes;
    }

    @Override
    public void sync(long time, Track track) throws Exception {
        vdoRes.sync(track.index, time);
    }

    @Override
    public ImgFrame generate(long time, Track track) {
        if (frame != null && frame.track != track) {
            initialized = false;
        }
        CountDownLatch cd = new CountDownLatch(1);
        if(!initialized){
            Gdx.app.postRunnable(()-> {
                if (texture == null) {
                    texture = new Texture(vdoRes.getWidth(), vdoRes.getHeight(), Pixmap.Format.RGBA8888);
                }
            frame = new ImgFrame(track);
            frame.setTexture(texture)
                .setTransform(new Transform(0, 0, vdoRes.getWidth(), vdoRes.getHeight(), 0));
            initialized = true;
            cd.countDown();
            });
            try {
                cd.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        try {
            vdoRes.get(track.index, time, frame);
        } catch (Exception e) {
            e.printStackTrace();
            frame.setPixels(null);
        }
        Transform t=frame.getTransform();
        t.x=0;
        t.y=0;
        t.width= vdoRes.getWidth();
        t.height= vdoRes.getHeight();
        t.rotation=0;
        frame.changed=true;
        return frame;
    }
    @Override
    public long getLengthPerExportFrame() {
        return vdoRes.getFrameLength();
    }
    @Override
    public long getDuration() {
        return vdoRes.getDuration();
    }
    @Override
    public void onDuplicate(Source<?> original) {
        VdoClipSrc src = (VdoClipSrc) original;
        this.vdoRes =src.vdoRes;
    }
}
