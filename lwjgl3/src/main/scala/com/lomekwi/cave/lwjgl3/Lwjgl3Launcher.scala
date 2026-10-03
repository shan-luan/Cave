package com.lomekwi.cave.lwjgl3

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Window
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3WindowListener
import com.lomekwi.cave.Main

import games.spooky.gdx.nativefilechooser.desktop.DesktopFileChooser

/** Launches the desktop (LWJGL3) application. */
object Lwjgl3Launcher {
  def main(args: Array[String]): Unit = {
    if (StartupHelper.startNewJvmIfRequired()) return; // This handles macOS support and helps on Windows.
    createApplication()
  }

  private def createApplication(): Lwjgl3Application = {
    val main = new Main(new DesktopFileChooser())
    new Lwjgl3Application(main, getDefaultConfiguration(main))
  }

  private def getDefaultConfiguration(main: Main): Lwjgl3ApplicationConfiguration = {
    val configuration: Lwjgl3ApplicationConfiguration = new Lwjgl3ApplicationConfiguration()
    configuration.setTitle("Cave")
    configuration.setWindowListener(new Lwjgl3WindowListener {
      override def created(window: Lwjgl3Window): Unit = {}

      override def iconified(isIconified: Boolean): Unit = {}

      override def maximized(isMaximized: Boolean): Unit = {}

      override def focusLost(): Unit = {}

      override def focusGained(): Unit = {}

      override def closeRequested(): Boolean = true

      override def filesDropped(files: Array[String]): Unit = {
        main.importDroppedFiles(files)
      }

      override def refreshRequested(): Unit = {}
    })
    //// Vsync limits the frames per second to what your hardware can display, and helps eliminate
    //// screen tearing. This setting doesn't always work on Linux, so the line after is a safeguard.
    configuration.useVsync(false)
    //// Limits FPS to the refresh rate of the currently active monitor, plus 1 to try to match fractional
    //// refresh rates. The Vsync setting above should limit the actual FPS to match the monitor.
    configuration.setForegroundFPS(Math.max(Lwjgl3ApplicationConfiguration.getDisplayMode.refreshRate + 1, 61))
    //// If you remove the above line and set Vsync to false, you can get unlimited FPS, which can be
    //// useful for testing performance, but can also be very stressful to some hardware.
    //// You may also need to configure GPU drivers to fully disable Vsync; this can cause screen tearing.

    configuration.setWindowedMode(1920, 1080)
    //// You can change these files; they are in lwjgl3/src/main/resources/ .
    //// They can also be loaded from the root of assets/ .
    configuration.setWindowIcon("libgdx128.png", "libgdx64.png", "libgdx32.png", "libgdx16.png")
    configuration.setPauseWhenLostFocus(false)
    configuration.setPauseWhenMinimized(false)
    configuration
  }
}
