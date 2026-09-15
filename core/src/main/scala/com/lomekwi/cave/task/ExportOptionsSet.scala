package com.lomekwi.cave.task

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Preferences
import com.badlogic.gdx.utils.Json
import com.badlogic.gdx.utils.JsonValue
import java.util


class ExportOptionsSet extends Json.Serializable {
  var presets: util.ArrayList[ExportOptions] = new util.ArrayList[ExportOptions]()
  var currentIndex: Int = 0

  presets.add(new ExportOptions())

  def current(): ExportOptions = {
    presets.get(currentIndex)
  }

  private def hasPrev: Boolean = {
    currentIndex > 0
  }

  def hasNext: Boolean = {
    currentIndex < presets.size() - 1
  }

  def prev(): Unit = {
    if (hasPrev) currentIndex -= 1
  }

  def next(): Unit = {
    if (hasNext) currentIndex += 1
  }

  def save(): Unit = {
    val prefs: Preferences = Gdx.app.getPreferences(ExportOptionsSet.PREFS_NAME)
    prefs.putString(ExportOptionsSet.KEY, new Json().toJson(this))
    prefs.flush()
  }

  override def write(json: Json): Unit = {
    json.writeValue("presets", presets)
    json.writeValue("currentIndex", currentIndex)
  }

  override def read(json: Json, jsonData: JsonValue): Unit = {
    val arr: JsonValue = jsonData.get("presets")
    presets.clear()
    var e: JsonValue = arr.child
    while (e != null) {
      presets.add(json.readValue(classOf[ExportOptions], e))
      e = e.next
    }
    currentIndex = jsonData.getInt("currentIndex", 0)
    if (presets.isEmpty) presets.add(new ExportOptions())
    if (currentIndex >= presets.size()) currentIndex = presets.size() - 1
  }
}

object ExportOptionsSet {
  private final val PREFS_NAME = "cave-export-options"
  private final val KEY = "options"

  def load(): ExportOptionsSet = {
    val prefs: Preferences = Gdx.app.getPreferences(PREFS_NAME)
    val json: String = prefs.getString(KEY, null)
    if (json == null || json.isEmpty) {
      return new ExportOptionsSet()
    }
    new Json().fromJson(classOf[ExportOptionsSet], json)
  }
}
