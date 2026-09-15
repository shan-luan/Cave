package com.lomekwi.cave.util

import com.lomekwi.cave.util.i18n.I18N.i18n

import java.io.File
import java.nio.file.Files
import java.nio.file.Path

object MimeType {

  private final val extensionToMimeType: Map[String, String] = Map(
    // 视频格式
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
    // 图片格式
    "png" -> "image/png",
    "jpg" -> "image/jpeg",
    "jpeg" -> "image/jpeg",
    "gif" -> "image/gif",
    "bmp" -> "image/bmp",
    "webp" -> "image/webp",
    "tiff" -> "image/tiff",
    "tif" -> "image/tiff",
    // 音频格式
    "mp3" -> "audio/mpeg",
    "wav" -> "audio/wav",
    "flac" -> "audio/flac",
    "aac" -> "audio/aac",
    "ogg" -> "audio/ogg",
    "wma" -> "audio/x-ms-wma",
    "m4a" -> "audio/x-m4a",
    // Cave 项目文件
    "cave" -> "application/x-cave-project"
  )

  /**
   * 检测文件的MIME类型，优先使用系统检测，失败时使用扩展名匹配
   * @param file 要检测的文件
   * @return MIME类型字符串，如果无法检测则返回null
   */
  def detectMimeType(file: File): String = {
    if (file == null || !file.exists()) {
      return null
    }

    val path: Path = file.toPath

    // 首先尝试使用系统检测
    try {
      val systemMimeType = Files.probeContentType(path)
      if (systemMimeType != null && !systemMimeType.isEmpty) {
        return systemMimeType
      }
    } catch {
      case _: Exception =>
      // 系统检测失败，继续使用扩展名检测
    }

    // 使用文件扩展名进行匹配
    val fileName = file.getName.toLowerCase()
    val lastDotIndex = fileName.lastIndexOf('.')
    if (lastDotIndex > 0 && lastDotIndex < fileName.length() - 1) {
      val extension = fileName.substring(lastDotIndex + 1)
      return extensionToMimeType.getOrElse(extension, null)
    }

    null
  }

  /**
   * 获取type/{@code *}形式的通配MIME类型
   * @param mimeType 完整的MIME类型，如 "video/mp4"
   * @return type/{@code *}形式，如 "video/{@code *}"
   */
  def getTypeWildcard(mimeType: String): String = {
    mimeType.substring(0, slashIndex(mimeType)) + "/*"
  }

  /**
   * 获取{@code *}/subtype形式的通配MIME类型
   * @param mimeType 完整的MIME类型，如 "video/mp4"
   * @return {@code *}/subtype形式，如 "{@code *}/mp4"
   */
  def getSubtypeWildcard(mimeType: String): String = {
    "*/" + mimeType.substring(slashIndex(mimeType) + 1)
  }

  /**
   * 获取{@code *}/{@code *}通配MIME类型
   * @return "{@code *}/{@code *}"
   */
  def getAllWildcard: String = {
    "*/*"
  }

  private def slashIndex(mimeType: String): Int = {
    if (mimeType == null || mimeType.isEmpty) {
      throw new IllegalArgumentException(i18n("MIME类型不能为空"))
    }
    val index = mimeType.indexOf('/')
    if (index == -1) {
      throw new IllegalArgumentException(i18n("无效的MIME类型格式: ") + mimeType)
    }
    index
  }
}
