package com.lomekwi.cave.pipeline.audio;

import com.lomekwi.cave.app.AppAudioOut;
import com.lomekwi.cave.pipeline.Source;
import com.lomekwi.cave.resource.decoder.AudDecRes;
import com.lomekwi.cave.resource.media.AudRes;
import com.lomekwi.cave.timeline.Track;

import java.io.Serial;

public class AudClipSrc extends Source<AudFrame> {
    private final AudRes audRes;
    @Serial
    private static final long serialVersionUID = 1L;

    public AudClipSrc(AudRes audRes) {
        this.audRes = audRes;
    }

    @Override
    protected AudFrame generate(long time, Track track) {
        AudDecRes decoder = (AudDecRes) audRes.getDecoder(track.index);

        if (frame == null) {
            frame = new AudFrame(AppAudioOut.SAMPLE_RATE, 2);
        }

        try {
            decoder.get(time, frame);
            if (frame.getSamples() == null) return null;
            return frame;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
