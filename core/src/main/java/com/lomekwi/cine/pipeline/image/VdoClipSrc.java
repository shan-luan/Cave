package com.lomekwi.cine.pipeline.image;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.lomekwi.cine.pipeline.Source;
import com.lomekwi.cine.resource.media.VdoRes;
import com.lomekwi.cine.service.VdoDecSvc;

public class VdoClipSrc implements Source<ImgProd> {
    private final VdoRes src;
    private final long offset;
    private final ImgProd prod= new ImgProd();
    public VdoClipSrc(VdoRes src, long offset) {
        this.src = src;
        this.offset = offset;
        prod.setTransform(new Transform(0,0,src.getWidth(),src.getHeight(),0))
            .setTexture(new Texture(src.getWidth(),src.getHeight(), Pixmap.Format.RGBA8888));
    }
    @Override
    public ImgProd get(long time) {
        long target = time + offset;
        try {
            prod.setPixels(VdoDecSvc.decode(target, src.getDecoder()));
        } catch (Exception e) {
            e.printStackTrace();
        }
        prod.changed = true;
        return prod;
    }
}
