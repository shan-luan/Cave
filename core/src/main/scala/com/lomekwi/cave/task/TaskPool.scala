package com.lomekwi.cave.task

import com.lomekwi.cave.util.i18n.I18N.i18n

import com.badlogic.gdx.Gdx
import com.lomekwi.cave.app.App

import java.util.HashSet
import java.util.Iterator
import java.util.Set

import scala.util.Using

class TaskPool extends java.lang.Iterable[Task] {
  private final val tasks: Set[Task] = new HashSet[Task]()

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
      Gdx.app.postRunnable(() => App.root.getToastManager().show(i18n("任务") + task.getName() + i18n("已完成")))
    })
  }

  override def iterator(): Iterator[Task] = {
    tasks.iterator()
  }
}
