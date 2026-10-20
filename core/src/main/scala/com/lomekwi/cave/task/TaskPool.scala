package com.lomekwi.cave.task

import com.lomekwi.cave.util.i18n.*
import com.lomekwi.cave.util.i18n.BasicLang.*
import com.lomekwi.cave.util.i18n.TransTargetType.given

import com.badlogic.gdx.Gdx
import com.lomekwi.cave.app.App


import scala.collection.mutable
import scala.util.Using

class TaskPool extends Iterable[Task] {
  private final val tasks: mutable.LinkedHashSet[Task] = mutable.LinkedHashSet.empty

  def submit(task: Task): Unit = {
    tasks += task
    App.workerExecutor.execute(() => {
      try {
        Using.resource(task) { t =>
          t.run()
        }
      } catch {
        case e: Exception =>
          // FIXME:ignore
      }
      tasks -= task
      Gdx.app.postRunnable(() => App.root.toastManager.show(("任务".in(Zh) | "Task ".in(En)).str() + task.getName + ("已完成".in(Zh) | " completed".in(En)).str()))
    })
  }

  override def iterator: Iterator[Task] = tasks.iterator
}
