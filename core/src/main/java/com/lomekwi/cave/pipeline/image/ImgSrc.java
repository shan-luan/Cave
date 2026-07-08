package com.lomekwi.cave.pipeline.image;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.lomekwi.cave.pipeline.Frame;
import com.lomekwi.cave.pipeline.Source;
import com.lomekwi.cave.resource.media.ImgRes;
import com.lomekwi.cave.timeline.Track;
import com.lomekwi.cave.ui.editpanel.detail.ImgSrcActor;

import java.io.Serial;
import java.util.concurrent.CountDownLatch;

public class ImgSrc extends Source<ImgFrame> {
    private ImgRes imgRes;
    private transient Texture texture;
    private volatile transient boolean initialized;
    @Serial
    private static final long serialVersionUID = 1L;

    public ImgSrc(ImgRes imgRes) {
        super();
        this.imgRes = imgRes;
    }

    public ImgRes getImgRes() {
        return imgRes;
    }

    @Override
    public void sync(long time, Track track) throws Exception {
        imgRes.sync(track.index, time);
    }

    @Override
    public ImgFrame generate(long time, Track track) {
        if (frame != null && frame.track != track) {
            initialized = false;
        }
        CountDownLatch cd = new CountDownLatch(1);
        if (!initialized) {
            Gdx.app.postRunnable(() -> {
                if (texture == null) {
                    texture = new Texture(imgRes.getWidth(), imgRes.getHeight(), Pixmap.Format.RGBA8888);
                }
                frame = new ImgFrame(track, this);
                frame.setTexture(texture)
                    .setTransform(new Transform(0, 0, imgRes.getWidth(), imgRes.getHeight(), 0));
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
            imgRes.get(track.index, time, frame);
        } catch (Exception e) {
            e.printStackTrace();
            frame.setPixels(null);
        }
        frame.getTransform().reset(0, 0, imgRes.getWidth(), imgRes.getHeight());
        frame.changed = true;
        return frame;
    }

    @Override
    public long getLengthPerExportFrame() {
        return imgRes.getFrameLength();
    }

    @Override
    public long getDuration() {
        return imgRes.getDuration();
    }

    @Override
    public Class<? extends Frame> getFrameType() {
        return ImgFrame.class;
    }

    @Override
    public String getDisplayName() {
        return "\u56fe\u7247\u6e90";
    }

    @Override
    public void onDuplicate(Source<?> original) {
        ImgSrc src = (ImgSrc) original;
        this.imgRes = src.imgRes;
    }

    @Override
    public Actor getDetailActor() {
        return new ImgSrcActor(this);
    }
}
