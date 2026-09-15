package com.lomekwi.cave.task

import com.lomekwi.cave.util.i18n.I18N.i18n

import com.badlogic.gdx.Gdx
import com.lomekwi.cave.app.App


import scala.util.Using
import java.util

class TaskPool extends java.lang.Iterable[Task] {
  private final val tasks: util.Set[Task] = new util.HashSet[Task]()

  def submit(task: Task): Unit = {
    tasks.add(task)
    App.workerExecutor.execute(() => {
      try {
        Using.resource(task) { t =>
          t.run()
        }
      } catch {
        case e: Exception =>
          // FIXME:ignore
      }
      tasks.remove(task)
      Gdx.app.postRunnable(() => App.root.getToastManager.show(i18n("任务") + task.getName + i18n("已完成")))
    })
  }

  override def iterator(): util.Iterator[Task] = {
    tasks.iterator()
  }
}
