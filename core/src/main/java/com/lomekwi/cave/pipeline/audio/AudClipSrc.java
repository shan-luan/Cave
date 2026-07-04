package com.lomekwi.cave.pipeline.audio;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.lomekwi.cave.app.AppAudioOut;
import com.lomekwi.cave.pipeline.Frame;
import com.lomekwi.cave.pipeline.Source;
import com.lomekwi.cave.resource.media.AudRes;
import com.lomekwi.cave.timeline.Track;
import com.lomekwi.cave.ui.editpanel.detail.AudClipSrcActor;

import java.io.Serial;

public class AudClipSrc extends Source<AudFrame> {
    private AudRes audRes;
    @Serial
    private static final long serialVersionUID = 1L;

    public AudClipSrc(AudRes audRes) {
        super();
        this.audRes = audRes;
    }

    public AudRes getAudRes() {
        return audRes;
    }

    @Override
    public void sync(long time, Track track) throws Exception {
        audRes.sync(track.index, time);
    }

    @Override
    protected AudFrame generate(long time, Track track) {

        if (frame == null || frame.track != track) {
            frame = new AudFrame(AppAudioOut.SAMPLE_RATE, 2, track, this);
        }

        try {
            audRes.get(track.index, time, frame);
            if (frame.getSamples() == null) return null;
            return frame;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public long getLengthPerExportFrame() {
        return audRes.getFrameLength();
    }
    @Override
    public long getDuration() {
        return audRes.getDuration();
    }
    @Override
    public Class<? extends Frame> getFrameType() {
        return AudFrame.class;
    }
    @Override
    public String getDisplayName() {
        return "音频源";
    }
    @Override
    public void onDuplicate(Source<?> original) {
        AudClipSrc src = (AudClipSrc) original;
        this.audRes = src.audRes;
    }
    @Override
    public Actor getDetailActor() {
        return new AudClipSrcActor(this);
    }
}
