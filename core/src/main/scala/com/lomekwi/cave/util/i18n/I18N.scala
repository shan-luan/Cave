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
    val result = keys.toArray
    var i = 0
    while (i < result.length) {
      result(i) = i18n(result(i))
      i += 1
    }
    result
  }
}
