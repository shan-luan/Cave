package com.lomekwi.cave.util

import com.lomekwi.cave.util.i18n.*
import com.lomekwi.cave.util.i18n.BasicLang.*

import java.io.File
import java.nio.file.Files
import java.nio.file.Path

object MimeType {

  private val extensionToMimeType: Map[String, String] = Map(
    "mkv" -> "video/x-matroska",
    "mp4" -> "video/mp4",
    "avi" -> "video/x-msvideo",
    "mov" -> "video/quicktime",
    "wmv" -> "video/x-ms-wmv",
    "flv" -> "video/x-flv",
    "webm" -> "video/webm",
    "m4v" -> "video/x-m4v",
    "mpeg" -> "video/mpeg",
    "mpg" -> "video/mpeg",
    "3gp" -> "video/3gpp",
    "png" -> "image/png",
    "jpg" -> "image/jpeg",
    "jpeg" -> "image/jpeg",
    "gif" -> "image/gif",
    "bmp" -> "image/bmp",
    "webp" -> "image/webp",
    "tiff" -> "image/tiff",
    "tif" -> "image/tiff",
    "mp3" -> "audio/mpeg",
    "wav" -> "audio/wav",
    "flac" -> "audio/flac",
    "aac" -> "audio/aac",
    "ogg" -> "audio/ogg",
    "wma" -> "audio/x-ms-wma",
    "m4a" -> "audio/x-m4a",
    "cave" -> "application/x-cave-project"
  )

  /**
   * 检测文件的 MIME 类型。
   */
  def detectMimeType(file: File): String = {
    if (file == null || !file.exists()) {
      null
    } else {
      val path: Path = file.toPath

      val systemMimeType = try {
        Files.probeContentType(path)
      } catch {
        case _: Exception => null
      }

      if (systemMimeType != null && systemMimeType.nonEmpty) {
        systemMimeType
      } else {
        val fileName = file.getName.toLowerCase()
        val lastDotIndex = fileName.lastIndexOf('.')
        if (lastDotIndex > 0 && lastDotIndex < fileName.length() - 1) {
          val extension = fileName.substring(lastDotIndex + 1)
          extensionToMimeType.getOrElse(extension, null)
        } else {
          null
        }
      }
    }
  }

  /**
   * 获取 type/`*` 形式的通配 MIME 类型。
   */
  def getTypeWildcard(mimeType: String): String = {
    mimeType.substring(0, slashIndex(mimeType)) + "/*"
  }

  /**
   * 获取 `*`/subtype 形式的通配 MIME 类型。
   */
  def getSubtypeWildcard(mimeType: String): String = {
    "*/" + mimeType.substring(slashIndex(mimeType) + 1)
  }

  /**
   * 获取全通配 MIME 类型。
   */
  def getAllWildcard: String = {
    "*/*"
  }

  private def slashIndex(mimeType: String): Int = {
    if (mimeType == null || mimeType.isEmpty) {
      throw new IllegalArgumentException(("MIME类型不能为空".in(Zh) | "MIME type must not be empty".in(En)).str())
    }
    val index = mimeType.indexOf('/')
    if (index == -1) {
      throw new IllegalArgumentException(("无效的MIME类型格式: ".in(Zh) | "Invalid MIME type format: ".in(En)).str() + mimeType)
    }
    index
  }
}
