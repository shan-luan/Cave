package com.lomekwi.cave.pipeline.text;

import static com.lomekwi.cave.util.Units.SECOND;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.kotcrab.vis.ui.VisUI;
import com.lomekwi.cave.pipeline.Frame;
import com.lomekwi.cave.pipeline.Source;
import com.lomekwi.cave.pipeline.image.Transform;
import com.lomekwi.cave.timeline.Track;
import com.lomekwi.cave.ui.editpanel.detail.TextSrcActor;

import java.io.Serial;
import java.util.concurrent.CountDownLatch;

public class TextSrc extends Source<TextFrame> {
    private String text;
    private transient BitmapFont font;
    private volatile transient boolean initialized;

    @Serial
    private static final long serialVersionUID = 1L;

    public TextSrc() {
        this("请输入文本");
    }

    public TextSrc(String text) {
        super();
        this.text = text;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    @Override
    public void sync(long time, Track track) throws Exception {}

    @Override
    protected TextFrame generate(long time, Track track) {
        if (frame != null && frame.track != track) {
            initialized = false;
        }
        CountDownLatch cd = new CountDownLatch(1);
        if (!initialized) {
            Gdx.app.postRunnable(() -> {
                if (font == null) {
                    font = VisUI.getSkin().getFont("default-font");
                }
                frame = new TextFrame(track, this);
                frame.setFont(font);
                frame.setTransform(new Transform(0, 0, 1, 1, 0));
                frame.initActor();
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
        frame.setText(text);
        frame.getTransform().reset(0, 0, frame.getBaseWidth(), frame.getBaseHeight());
        return frame;
    }

    @Override
    public long getLengthPerExportFrame() {
        return SECOND;
    }

    @Override
    public long getDuration() {
        return text.length()*SECOND;
    }

    @Override
    public Class<? extends Frame> getFrameType() {
        return TextFrame.class;
    }

    @Override
    public String getDisplayName() {
        return "文本源";
    }

    @Override
    public Actor getDetailActor() {
        return new TextSrcActor(this);
    }

    @Override
    public void onDuplicate(Source<?> original) {
        TextSrc src = (TextSrc) original;
        this.text = src.text;
    }
}
