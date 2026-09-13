package com.lomekwi.cave.app


import com.google.common.eventbus.EventBus
import com.lomekwi.cave.app.copy.CopyManager
import com.lomekwi.cave.app.shortcut.ShortcutManager
import com.lomekwi.cave.pipeline.NodeRegistry
import com.lomekwi.cave.resource.media.MediaFactory
import com.lomekwi.cave.task.TaskPool
import com.lomekwi.cave.ui.Root

import games.spooky.gdx.nativefilechooser.NativeFileChooser

import java.util.concurrent.{ExecutorService, Executors}

object App {
  var fileChooser: NativeFileChooser = null
  var audioOut: AppAudioOut = null
  final val appEventBus = new EventBus()
  final val workerExecutor: ExecutorService = Executors.newCachedThreadPool((r: Runnable) => {
    val thread = new Thread(r)
    thread.setDaemon(true)
    thread
  })
  final val taskPool = new TaskPool()
  var root: Root = null
  final val shortcutManager = new ShortcutManager()
  final val copyManager = new CopyManager()
  final val mediaFactory = new MediaFactory()
  final val nodeRegistry = new NodeRegistry()

}
