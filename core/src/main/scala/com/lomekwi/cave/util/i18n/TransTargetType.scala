package com.lomekwi.cave.util.i18n

import com.lomekwi.cave.util.i18n.BasicLang.{En, Zh}

object TransTargetType {
  private type targetType = En.type & Zh.type

  given target[L <: targetType]: Target[L] = new Target[L] {}
}
