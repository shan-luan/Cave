package com.lomekwi.cave.app

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Preferences
import com.lomekwi.cave.util.i18n.BasicLang.{En, Zh}
import com.lomekwi.cave.util.i18n.{Lang, Qin}

class LanguageManager {
  def load(): Unit = Qin.currentLang = persistedLang

  def persist(lang: Lang): Unit = {
    val prefs: Preferences = Gdx.app.getPreferences(LanguageManager.PREFS_NAME)
    prefs.putString(LanguageManager.KEY, lang.toString)
    prefs.flush()
  }

  def persistedLang: Lang = {
    val prefs: Preferences = Gdx.app.getPreferences(LanguageManager.PREFS_NAME)
    val stored: String = prefs.getString(LanguageManager.KEY, null)
    LanguageManager.supported.find((l: Lang) => l.toString == stored).getOrElse(En)
  }
}

object LanguageManager {
  private val PREFS_NAME = "cave-language"
  private val KEY = "language"
  val supported: Seq[Lang] = Seq(Zh, En)
}
