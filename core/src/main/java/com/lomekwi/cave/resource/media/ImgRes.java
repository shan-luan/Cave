package com.lomekwi.cave.resource.media;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.lomekwi.cave.pipeline.Frame;
import com.lomekwi.cave.pipeline.image.ImgFrame;
import com.lomekwi.cave.resource.decoder.DecRes;
import com.lomekwi.cave.resource.decoder.ImgDecRes;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.nio.ByteBuffer;

import static com.lomekwi.cave.util.Units.SECOND;

public class ImgRes extends MedRes implements Previewable {
    private int width;
    private int height;
    private transient ByteBuffer cachedPixels;
    private transient int unpackRowLength;
    private transient volatile boolean decoded;
    private transient Texture texture;


    @Serial
    private static final long serialVersionUID = 1L;

    public ImgRes(String path) {
        super(path);
        this.duration = 5L * SECOND;
    }

    @Override
    protected ImgDecRes newDecoder() {
        return new ImgDecRes(this);
    }

    @Override
    protected void generateMetadata(DecRes<?> metadataDecRes) {
        ImgDecRes idr = (ImgDecRes) metadataDecRes;
        width = idr.getWidth();
        height = idr.getHeight();
        ImgFrame tmp = new ImgFrame(null);
        try {
            idr.get(0, tmp);
            cachedPixels = idr.getCachedPixels();
            unpackRowLength = idr.getUnpackRowLength();
            decoded = true;
        } catch (Exception e) {
            // Will decode lazily on first get()
        }
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public long getFrameLength() {
        return SECOND / 30;
    }

    @Override
    public void get(int trackIndex, long time, Frame frame) throws Exception {
        if (!decoded) {
            try (ImgDecRes dec = new ImgDecRes(this)) {
                dec.start();
                ImgFrame tmp = new ImgFrame(null);
                dec.get(0, tmp);
                cachedPixels = dec.getCachedPixels();
                unpackRowLength = dec.getUnpackRowLength();
            }
            decoded = true;
        }
        ImgFrame imgFrame = (ImgFrame) frame;
        imgFrame.setPixels(cachedPixels);
        imgFrame.setUnpackRowLength(unpackRowLength);
    }

    @Override
    public void sync(int trackIndex, long time) {
    }

    public Texture getTexture() {
        if (texture != null) return texture;
        if (cachedPixels == null) return null;
        texture = new Texture(width, height, Pixmap.Format.RGBA8888);
        texture.bind();
        cachedPixels.rewind();
        Gdx.gl.glPixelStorei(GL30.GL_UNPACK_ROW_LENGTH, unpackRowLength);
        Gdx.gl.glTexSubImage2D(GL20.GL_TEXTURE_2D, 0, 0, 0, width, height,
            GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE, cachedPixels);
        Gdx.gl.glPixelStorei(GL30.GL_UNPACK_ROW_LENGTH, 0);
        return texture;
    }

    @Override
    public Texture getPreview(long time) {
        return getTexture();
    }

    @Override
    public long getPreviewInterval() {
        return SECOND;
    }

    @Override
    public void close() throws Exception {
        super.close();
        cachedPixels = null;
        decoded = false;
        if (texture != null) {
            texture.dispose();
            texture = null;
        }
    }

    @Serial
    private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
        ois.defaultReadObject();
        decoded = false;
        cachedPixels = null;
    }
}
