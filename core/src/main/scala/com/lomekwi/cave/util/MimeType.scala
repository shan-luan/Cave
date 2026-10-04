package com.lomekwi.cave.util

import com.lomekwi.cave.util.i18n.I18N.i18n

import java.io.File
import java.nio.file.Files
import java.nio.file.Path

object MimeType {

  private final val extensionToMimeType: Map[String, String] = Map(
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
   * 检测文件的 MIME 类型，优先使用系统检测，失败时使用扩展名匹配
   * @param file 要检测的文件
   * @return MIME 类型字符串，如果无法检测则返回 null
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

      if (systemMimeType != null && !systemMimeType.isEmpty) {
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
   * 获取 type/`*` 形式的通配 MIME 类型
   * @param mimeType 完整的 MIME 类型，如 "video/mp4"
   * @return 将 subtype 部分替换为 `*`，如 "video/mp4" 得到通配形式
   */
  def getTypeWildcard(mimeType: String): String = {
    mimeType.substring(0, slashIndex(mimeType)) + "/*"
  }

  /**
   * 获取 `*`/subtype 形式的通配 MIME 类型
   * @param mimeType 完整的 MIME 类型，如 "video/mp4"
   * @return 将 type 部分替换为 `*`，如 "video/mp4" 得到子类型通配形式
   */
  def getSubtypeWildcard(mimeType: String): String = {
    "*/" + mimeType.substring(slashIndex(mimeType) + 1)
  }

  /**
   * 获取全通配 MIME 类型
   * @return type 与 subtype 均为 `*` 的字符串
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
