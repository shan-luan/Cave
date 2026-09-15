package com.lomekwi.cave.util.i18n

object I18N {
  //TODO
  def i18n(key: String): String = {
    key
  }
  def i18n(keys: String*): Array[String] = {
    keys.map(i18n).toArray
  }
}
