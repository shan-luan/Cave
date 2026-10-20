/**
 * 从[[https://github.com/shan-luan/Qin]]中复制
 */
/**
 * Copyright (c) 2026 shan-luan
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.lomekwi.cave.util.i18n

import BasicLang.*

import scala.collection.{immutable, mutable}
import scala.collection.mutable.{HashMap, Map}
import scala.reflect.Enum

trait Lang extends Enum {
  val localizedCache: mutable.Map[String, Localized[this.type]] = new mutable.HashMap()
}

/**
 * See [[https://w3techs.com/technologies/overview/content_language]].
 *
 * Top 5.
 *
 * I added Chinese because I am ;)
 */
enum BasicLang extends Lang {
  case De //German
  case En //English
  case Es //Spanish
  case Fr //French
  case Ja //Japanese
  case Zh //Chinese
}

trait Target[L <: Lang]

object Target {
  /**
   * If you define any more specific and "matching" Target Given, this won’t be used here.
   */
  @deprecated("Translation missing. This will fall back to English or random language at runtime.")
  given fallback[L <: Lang]: Target[L] = new Target[L] {}
}

case class Localized[L <: Lang] private (supported: immutable.Map[Lang, String]) {
  def |[L2 <: Lang](l: Localized[L2]): Localized[L & L2] =
    Localized.of[L & L2](supported ++ l.supported)

  def str()(using Target[L]): String =
    supported.get(Qin.currentLang).orElse(supported.get(En)).getOrElse(supported.head._2)
}

object Localized {
  private[i18n] def of[L <: Lang](supported: immutable.Map[Lang, String]): Localized[L] =
    new Localized(supported)
}

extension (t: String)
  def in(l: Lang): Localized[l.type] =
    l.localizedCache.getOrElseUpdate(t, Localized.of[l.type](immutable.Map(l -> t)))

object Qin {
  var currentLang: Lang = En
}
