package com.lomekwi.cave.pipeline.audio;

import com.lomekwi.cave.pipeline.Source;
import com.lomekwi.cave.resource.decoder.AudDecRes;
import com.lomekwi.cave.resource.media.AudRes;
import com.lomekwi.cave.timeline.Track;

public class AudClipSrc extends Source<AudFrame> {
    private final AudRes audRes;
    private AudFrame frame;
    private static final long serialVersionUID = 1L;

    public AudClipSrc(AudRes audRes) {
        this.audRes = audRes;
    }

    @Override
    protected AudFrame generate(long time, Track track) {
        if (frame == null) {
            frame = new AudFrame();
        }
        AudDecRes decoder = (AudDecRes) audRes.getDecoder(track);
        if (!decoder.isInitialized()) {
            try {
                decoder.start();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        try {
            short[] samples = decoder.decodeFrameAtTime(time);
            if (samples == null) return null;
            frame.setSamples(samples).setSampleRate(44100).setChannels(1).setTime(time);
            return frame;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
