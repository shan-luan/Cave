package com.lomekwi.cave.app;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.AudioDevice;

public class AppAudioOut {
    public static final int SAMPLE_RATE = 44100;
    private final AudioDevice audioDevice;

    public AppAudioOut() {
        audioDevice = Gdx.audio.newAudioDevice(SAMPLE_RATE, false);
    }

    public AudioDevice getAudioDevice() {
        return audioDevice;
    }
}
