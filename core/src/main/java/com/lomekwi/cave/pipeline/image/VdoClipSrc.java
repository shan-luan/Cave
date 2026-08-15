package com.lomekwi.cave.pipeline.image;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.lomekwi.cave.pipeline.Source;
import com.lomekwi.cave.resource.media.VdoRes;
import com.lomekwi.cave.timeline.Segment;
import com.lomekwi.cave.timeline.Track;
import com.lomekwi.cave.ui.editpanel.inspector.SourceActor;
import com.lomekwi.cave.ui.editpanel.inspector.VdoClipSrcActor;
import com.lomekwi.cave.ui.editpanel.previewarea.TransFrameActor;
import com.lomekwi.cave.ui.editpanel.tlarea.SegActor;
import com.lomekwi.cave.ui.editpanel.tlarea.VdoSegActor;

import java.io.Serial;
import java.util.concurrent.CountDownLatch;

public class VdoClipSrc extends Source<ImgFrame> {
    private VdoRes vdoRes;
    private transient Texture texture;
    private transient TransFrameActor actor;
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
            frame = new ImgFrame(track, this);
            frame.setTexture(texture)
                .setTransform(new Transform(0, 0, 0));
            if (actor == null) {
                actor = new TransFrameActor(frame);
            } else {
                actor.rebind(frame);
            }
            frame.setActor(actor);
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
        frame.getTransform().reset(0, 0);
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
    public Class<ImgFrame> getFrameType() {
        return ImgFrame.class;
    }
    @Override
    public String getDisplayName() {
        return "视频源";
    }
    @Override
    public void onDuplicate(Source<?> original) {
        VdoClipSrc src = (VdoClipSrc) original;
        this.vdoRes =src.vdoRes;
    }
    @Override
    public SourceActor getSourceActor() {
        return new VdoClipSrcActor(this);
    }

    @Override
    public SegActor createSegActor(Segment segment) {
        return new VdoSegActor(segment);
    }
}
