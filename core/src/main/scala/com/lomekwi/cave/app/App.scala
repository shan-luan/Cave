package com.lomekwi.cave.app


import com.google.common.eventbus.EventBus
import com.lomekwi.cave.app.copy.CopyManager
import com.lomekwi.cave.app.shortcut.ShortcutManager
import com.lomekwi.cave.pipeline.NodeRegistry
import com.lomekwi.cave.resource.media.MediaFactory
import com.lomekwi.cave.task.TaskPool
import com.lomekwi.cave.ui.Root
import com.lomekwi.cave.ui.node.CardWidgetsRegistry

import games.spooky.gdx.nativefilechooser.NativeFileChooser

import java.util.concurrent.{ExecutorService, Executors}
import scala.compiletime.uninitialized

object App {
  var fileChooser: NativeFileChooser = uninitialized
  var audioOut: AppAudioOut = uninitialized
  val appEventBus = new EventBus()
  val workerExecutor: ExecutorService = Executors.newCachedThreadPool((r: Runnable) => {
    val thread = new Thread(r)
    thread.setDaemon(true)
    thread
  })
  val taskPool = new TaskPool()
  var root: Root = uninitialized
  val shortcutManager = new ShortcutManager()
  val languageManager = new LanguageManager()
  val copyManager = new CopyManager()
  val mediaFactory = new MediaFactory()
  val nodeRegistry = new NodeRegistry()
  val cardWidgetsRegistry = new CardWidgetsRegistry()

}
