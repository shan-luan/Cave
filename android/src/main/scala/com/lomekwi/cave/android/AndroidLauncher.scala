package com.lomekwi.cave.android

import android.os.Bundle

import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import com.lomekwi.cave.Main

import games.spooky.gdx.nativefilechooser.android.AndroidFileChooser

/** Launches the Android application. */
class AndroidLauncher extends AndroidApplication {
    override protected def onCreate(savedInstanceState: Bundle): Unit = {
        super.onCreate(savedInstanceState);
        val configuration: AndroidApplicationConfiguration = new AndroidApplicationConfiguration();
        configuration.useImmersiveMode = true; // Recommended, but not required.
        initialize(new Main(new AndroidFileChooser(this)), configuration);
    }
}
