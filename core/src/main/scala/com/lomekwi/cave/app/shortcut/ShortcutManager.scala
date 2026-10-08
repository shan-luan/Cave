package com.lomekwi.cave.app.shortcut

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Preferences

import scala.collection.mutable

class ShortcutManager {
  private final val actionToKeys: mutable.LinkedHashMap[ShortcutAction, mutable.ArrayBuffer[Int]] = mutable.LinkedHashMap.empty
  private final val registeredActions: mutable.LinkedHashSet[ShortcutAction] = mutable.LinkedHashSet.empty

  def register(action: ShortcutAction, keyCode: Int*): Unit = {
    val keys = actionToKeys.getOrElseUpdate(action, mutable.ArrayBuffer.empty[Int])
    keys.clear()
    keys ++= keyCode
    registeredActions.add(action)
  }

  def resetToDefault(action: ShortcutAction): Unit = {
    register(action, action.defaultKeys()*)
  }

  def getKeys(action: ShortcutAction): mutable.ArrayBuffer[Int] = {
    actionToKeys.getOrElse(action, mutable.ArrayBuffer.empty[Int])
  }

  def getAllActions: mutable.LinkedHashSet[ShortcutAction] = {
    mutable.LinkedHashSet.from(registeredActions)
  }

  def isActive(action: ShortcutAction): Boolean = {
    val keys = actionToKeys.getOrElse(action, mutable.ArrayBuffer.empty[Int])
    keys.nonEmpty && keys.forall(key => Gdx.input.isKeyPressed(key))
  }

  def load(): Unit = {
    val prefs: Preferences = Gdx.app.getPreferences(ShortcutManager.PREFS_NAME)
    for (action <- registeredActions) {
      val v: String = prefs.getString(action.toString, null)
      if (v != null && !v.isEmpty) {
        val keys: Array[Int] = v.split(",").map((s: String) => Integer.parseInt(s))
        register(action, keys*)
      }
    }
  }

  def persist(): Unit = {
    val prefs: Preferences = Gdx.app.getPreferences(ShortcutManager.PREFS_NAME)
    prefs.clear()
    for (action <- registeredActions) {
      val keys = actionToKeys.getOrElse(action, mutable.ArrayBuffer.empty[Int])
      if (keys.nonEmpty) {
        val v: String = keys.mkString(",")
        prefs.putString(action.toString, v)
      }
    }
    prefs.flush()
  }
}

object ShortcutManager {
  private final val PREFS_NAME = "cave-shortcuts"
}
