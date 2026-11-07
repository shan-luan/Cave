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
    if (StartupHelper.startNewJvmIfRequired()) return;
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
    configuration.useVsync(false)
    configuration.setForegroundFPS(Math.max(Lwjgl3ApplicationConfiguration.getDisplayMode.refreshRate + 1, 61))

    configuration.setWindowedMode(1920, 1080)
    configuration.setWindowIcon("libgdx128.png", "libgdx64.png", "libgdx32.png", "libgdx16.png")
    configuration.setPauseWhenLostFocus(false)
    configuration.setPauseWhenMinimized(false)
    configuration.setStencilBits(8)
    configuration
  }
}
