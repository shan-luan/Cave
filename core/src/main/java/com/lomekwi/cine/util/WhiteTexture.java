package com.lomekwi.cine.util;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
//FIXME:当GL上下文丢失时无法恢复
public class WhiteTexture extends Texture {
    private static Pixmap pixmap;
    private static WhiteTexture instance;
    private WhiteTexture(){
        super(getPixmap());
        pixmap.dispose();
        pixmap=null;
    }
    private static Pixmap getPixmap(){
        pixmap=new Pixmap(1,1, Pixmap.Format.RGBA8888);
        pixmap.setColor(1,1,1,1);
        pixmap.fill();
        return pixmap;
    }
    public static WhiteTexture getInstance(){
        if(instance==null){
            instance=new WhiteTexture();
        }
        return instance;
    }
    @Override
    public void dispose() {
        Gdx.app.error("WhiteTexture", "This object cannot be disposed.");
    }
}
