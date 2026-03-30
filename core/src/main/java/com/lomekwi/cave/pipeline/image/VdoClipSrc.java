package com.lomekwi.cave.pipeline.image;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.lomekwi.cave.pipeline.Source;
import com.lomekwi.cave.resource.media.VdoRes;
import com.lomekwi.cave.service.VdoDecSvc;

import org.bytedeco.javacv.FrameGrabber;

import java.nio.ByteBuffer;

public class VdoClipSrc extends Source<ImgProd> {
    private final VdoRes src;
    private transient Texture texture;
    private transient ImgProd prod;
    private transient boolean initialized;
    private static final long serialVersionUID = 1L;
    public VdoClipSrc(VdoRes src) {
        this.src = src;
    }
    @Override
    public ImgProd generate(long time) {
        if(!initialized){
            texture=new Texture(src.getWidth(),src.getHeight(), Pixmap.Format.RGBA8888);
            prod=new ImgProd();
            prod.setTexture(texture)
                .setTransform(new Transform(0,0,src.getWidth(),src.getHeight(),0));
            initialized=true;
        }
        ByteBuffer pixels;
        try {
            pixels=VdoDecSvc.decode(time, src.getDecoder());
        } catch (Exception e) {
            e.printStackTrace();
            pixels=null;
        }
        prod.setPixels(pixels);
        Transform t=prod.getTransform();
        t.x=0;
        t.y=0;
        t.width=src.getWidth();
        t.height=src.getHeight();
        t.rotation=0;
        prod.changed=true;
        return prod;
    }
}
