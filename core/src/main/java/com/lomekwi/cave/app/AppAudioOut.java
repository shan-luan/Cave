package com.lomekwi.cave.app;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.AudioDevice;

public class AppAudioOut {
    public static final int SAMPLE_RATE = 44100;
    private static AppAudioOut INSTANCE;
    private AudioDevice audioDevice;
    private AppAudioOut() {
        audioDevice = Gdx.audio.newAudioDevice(SAMPLE_RATE, false);
    }
    public static AppAudioOut getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new AppAudioOut();
        }
        return INSTANCE;
    }

    public AudioDevice getAudioDevice() {
        return audioDevice;
    }
}
