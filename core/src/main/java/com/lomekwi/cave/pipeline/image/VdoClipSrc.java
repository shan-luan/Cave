package com.lomekwi.cave.pipeline.image;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.lomekwi.cave.pipeline.Source;
import com.lomekwi.cave.resource.media.VdoRes;
import com.lomekwi.cave.timeline.Track;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;

public class VdoClipSrc extends Source<ImgFrame> {
    private final VdoRes src;
    private transient Texture texture;
    private transient ImgFrame frame;
    private volatile transient boolean initialized;
    private static final long serialVersionUID = 1L;
    public VdoClipSrc(VdoRes src) {
        this.src = src;
    }
    @Override
    public ImgFrame generate(long time, Track track) {
        CountDownLatch cd = new CountDownLatch(1);
        if(!initialized){
            Gdx.app.postRunnable(()-> {
                texture = new Texture(src.getWidth(), src.getHeight(), Pixmap.Format.RGBA8888);
            frame = new ImgFrame();
            frame.setTexture(texture)
                .setTransform(new Transform(0, 0, src.getWidth(), src.getHeight(), 0));
            initialized = true;
            cd.countDown();
            });
            try {
                cd.await();
            } catch (InterruptedException e) {
                return null;
            }
        }
        ByteBuffer pixels;
        try {
            pixels=src.getDecoder(track).decodeFrameAtTime(time);
        } catch (Exception e) {
            e.printStackTrace();
            pixels=null;
        }
        frame.setPixels(pixels);
        Transform t=frame.getTransform();
        t.x=0;
        t.y=0;
        t.width=src.getWidth();
        t.height=src.getHeight();
        t.rotation=0;
        frame.changed=true;
        return frame;
    }
}
