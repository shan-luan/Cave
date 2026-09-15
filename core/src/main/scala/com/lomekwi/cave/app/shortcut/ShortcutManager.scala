package com.lomekwi.cave.app.shortcut

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Preferences
import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.Multimap

import java.util
import java.util.{Collections}
import java.util.stream.Collectors

import scala.jdk.CollectionConverters.*

class ShortcutManager {
  private final val actionToKeys: Multimap[ShortcutAction, Integer] = ArrayListMultimap.create[ShortcutAction, Integer]()
  private final val registeredActions: util.Set[ShortcutAction] = new util.LinkedHashSet[ShortcutAction]()

  def register(action: ShortcutAction, keyCode: Int*): Unit = {
    actionToKeys.removeAll(action)
    for (k <- keyCode) {
      actionToKeys.put(action, k)
    }
    registeredActions.add(action)
  }

  def resetToDefault(action: ShortcutAction): Unit = {
    register(action, action.defaultKeys()*)
  }

  def getKeys(action: ShortcutAction): util.Collection[Integer] = {
    actionToKeys.get(action)
  }

  def getAllActions: util.Set[ShortcutAction] = {
    Collections.unmodifiableSet(registeredActions)
  }

  def isActive(action: ShortcutAction): Boolean = {
    val keys = actionToKeys.get(action)
    !keys.isEmpty && keys.asScala.forall(key => Gdx.input.isKeyPressed(key))
  }

  def load(): Unit = {
    val prefs: Preferences = Gdx.app.getPreferences(ShortcutManager.PREFS_NAME)
    for (action <- registeredActions.asScala) {
      val v: String = prefs.getString(action.toString, null)
      if (v != null && !v.isEmpty) {
        val keys: Array[Int] = util.Arrays.stream(v.split(","))
          .mapToInt((s: String) => Integer.parseInt(s))
          .toArray
        register(action, keys*)
      }
    }
  }

  def persist(): Unit = {
    val prefs: Preferences = Gdx.app.getPreferences(ShortcutManager.PREFS_NAME)
    prefs.clear()
    for (action <- registeredActions.asScala) {
      val keys: util.Collection[Integer] = actionToKeys.get(action)
      if (!keys.isEmpty) {
        val v: String = keys.stream().map((i: Integer) => String.valueOf(i)).collect(Collectors.joining(","))
        prefs.putString(action.toString, v)
      }
    }
    prefs.flush()
  }
}

object ShortcutManager {
  private final val PREFS_NAME = "cave-shortcuts"
}
