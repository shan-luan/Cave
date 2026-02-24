package com.lomekwi.cave.pipeline.image;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.lomekwi.cave.pipeline.Source;
import com.lomekwi.cave.resource.media.VdoRes;
import com.lomekwi.cave.service.VdoDecSvc;

import java.nio.ByteBuffer;

public class VdoClipSrc implements Source<ImgProd> {
    private final VdoRes src;
    private final long offset;
    private Texture texture;
    private final ImgProd prod= new ImgProd();
    public VdoClipSrc(VdoRes src, long offset) {
        this.src = src;
        this.offset = offset;
    }
    @Override
    public ImgProd get(long time) {
        long target = time + offset;
        ByteBuffer pixels;
        try {
            pixels=VdoDecSvc.decode(target, src.getDecoder());
        } catch (Exception e) {
            e.printStackTrace();
            pixels=null;
        }
        prod.setPixels(pixels);
        if(texture==null){
            texture=new Texture(src.getWidth(),src.getHeight(), Pixmap.Format.RGBA8888);
            prod.setTexture(texture)
                .setTransform(new Transform(0,0,src.getWidth(),src.getHeight(),0));
        }
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
