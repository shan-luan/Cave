package com.lomekwi.cave.util.legacy_i18n

//WIP
object I18N {
  inline def i18n(key: String): String = {
    key
  }
  inline def i18n(keys: String*): Array[String] = {
    keys.map(i18n).toArray
  }
}
