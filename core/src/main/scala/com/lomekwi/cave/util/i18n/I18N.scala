package com.lomekwi.cave.util.i18n

@Deprecated
object I18N {
  //TODO
  @Deprecated
  def i18n(key: String): String = {
    key
  }
  @Deprecated
  def i18n(keys: String*): Array[String] = {
    keys.map(i18n).toArray
  }
}
