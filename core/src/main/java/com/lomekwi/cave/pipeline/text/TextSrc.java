package com.lomekwi.cave.pipeline.text;

import static com.lomekwi.cave.util.Units.SECOND;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.lomekwi.cave.pipeline.NumInPort;
import com.lomekwi.cave.pipeline.Source;
import com.lomekwi.cave.pipeline.StringInPort;
import com.lomekwi.cave.pipeline.num.NumFrame;
import com.lomekwi.cave.pipeline.image.Transform;
import com.lomekwi.cave.resource.media.FontRes;
import com.lomekwi.cave.timeline.Segment;
import com.lomekwi.cave.timeline.Track;
import com.lomekwi.cave.ui.editpanel.previewarea.TransFrameActor;
import com.lomekwi.cave.ui.editpanel.tlarea.SegActor;
import com.lomekwi.cave.ui.editpanel.tlarea.TextSegActor;

import java.io.Serial;
import java.util.concurrent.CountDownLatch;

public class TextSrc extends Source<TextFrame> {
    private final StringInPort textIn = addInPort(new StringInPort("文本", "请输入文本"));
    private final NumInPort fontSizeIn = addInPort(new NumInPort("字号", frame(48)));
    private transient FontRes fontRes;
    private transient BitmapFont font;
    /** 已生成的字体字号，用于检测端口字号被外部修改后需要重建字体。 */
    private transient int generatedFontSize;
    private transient TransFrameActor actor;
    private volatile transient boolean initialized;

    @Serial
    private static final long serialVersionUID = 1L;

    public TextSrc() {
        this("请输入文本");
    }

    public TextSrc(String text) {
        super();
        textIn.setDefaultData(text);
        fontRes = new FontRes("font/noto.otf");
    }

    private static NumFrame frame(double v) {
        NumFrame f = new NumFrame(null);
        f.setVal(v);
        return f;
    }

    public String getText() {
        return textIn.getDefaultData();
    }

    public void setText(String text) {
        textIn.setDefaultData(text);
    }

    public FontRes getFontRes() {
        return fontRes;
    }

    public void setFontRes(FontRes fontRes) {
        if (this.fontRes != null && this.fontRes != fontRes) {
            this.fontRes.close();
        }
        this.fontRes = fontRes;
    }

    public int getFontSize() {
        return (int) fontSizeIn.getDefaultData().getVal();
    }

    public void setFontSize(int fontSize) {
        fontSizeIn.getDefaultData().setVal(fontSize);
        invalidateFont();
    }

    public String getFontPath() {
        return fontRes != null ? fontRes.getPath() : "";
    }

    public void setFontPath(String path) {
        if (fontRes != null) {
            fontRes.close();
        }
        fontRes = new FontRes(path);
        invalidateFont();
    }

    private void invalidateFont() {
        font = null;
        initialized = false;
    }

    @Override
    public void sync(long time, Track track) throws Exception {}

    @Override
    protected TextFrame generate(long time, Track track) {
        if (frame != null && frame.track != track) {
            initialized = false;
        }
        if (font != null && generatedFontSize != getFontSize()) {
            // spinner 等外部直接改了端口值：丢弃旧字体，重建帧
            font = null;
            initialized = false;
        }
        CountDownLatch cd = new CountDownLatch(1);
        if (!initialized) {
            Gdx.app.postRunnable(() -> {
                if (font == null) {
                    font = fontRes.getFont(getFontSize());
                    generatedFontSize = getFontSize();
                }
                frame = new TextFrame(track, this);
                frame.setFont(font);
                frame.setTransform(new Transform(0, 0, 0));
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
        frame.setText(getText());
        frame.getTransform().reset(0, 0);
        return frame;
    }

    @Override
    public long getLengthPerExportFrame() {
        return SECOND;
    }

    @Override
    public long getDuration() {
        return Long.MAX_VALUE;
    }

    @Override
    public Class<TextFrame> getFrameType() {
        return TextFrame.class;
    }

    @Override
    public String getDisplayName() {
        return "文本源";
    }

    @Override
    public SegActor createSegActor(Segment segment) {
        return new TextSegActor(segment);
    }

    @Override
    public void onDuplicate(Source<?> original) {
        TextSrc src = (TextSrc) original;
        this.textIn.setDefaultData(src.getText());
        this.fontSizeIn.getDefaultData().setVal(src.getFontSize());
        this.fontRes = new FontRes(src.fontRes.getPath());
    }
}
