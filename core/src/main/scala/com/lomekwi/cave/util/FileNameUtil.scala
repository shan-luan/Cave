package com.lomekwi.cave.util

import java.io.File

object FileNameUtil {
  def getExtension(file: File): String = {
    if (file == null) {
      ""
    } else {
      val name = file.getName
      val dotIndex = name.lastIndexOf('.')
      if (dotIndex >= 0 && dotIndex < name.length() - 1) name.substring(dotIndex + 1) else ""
    }
  }
  def ensureExtension(name: String, ext: String): String = {
    if (name == null || ext == null) {
      name
    } else {
      val dotExt = if (ext.startsWith(".")) ext else "." + ext
      if (name.toLowerCase().endsWith(dotExt.toLowerCase())) name else name + dotExt
    }
  }
  def ensureExtension(file: File, ext: String): File = {
    if (file == null) {
      null
    } else {
      val name = file.getName
      val newName = ensureExtension(name, ext)
      if (name.equals(newName)) file else new File(file.getParent, newName)
    }
  }
}
