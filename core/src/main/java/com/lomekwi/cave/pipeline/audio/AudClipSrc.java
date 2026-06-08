package com.lomekwi.cave.pipeline.audio;

import com.lomekwi.cave.app.AppAudioOut;
import com.lomekwi.cave.pipeline.Source;
import com.lomekwi.cave.resource.media.AudRes;
import com.lomekwi.cave.timeline.Track;

import java.io.Serial;

public class AudClipSrc extends Source<AudFrame> {
    private final AudRes audRes;
    @Serial
    private static final long serialVersionUID = 1L;

    public AudClipSrc(AudRes audRes) {
        super();
        this.audRes = audRes;
    }

    @Override
    public void sync(long time, Track track) throws Exception {
        audRes.sync(track.index, time);
    }

    @Override
    protected AudFrame generate(long time, Track track) {

        if (frame == null) {
            frame = new AudFrame(AppAudioOut.SAMPLE_RATE, 2);
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
}
