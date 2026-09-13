package com.lomekwi.cave.resource.media

import com.lomekwi.cave.util.MimeType
import org.bytedeco.javacv.FFmpegFrameGrabber

import java.util.ArrayList
import java.util.HashMap
import java.util.List
import java.util.Map
import java.util.function.Function

class MediaFactory {
  import MediaFactory.*

  private final val map: Map[String, Function[String, MedRes]] = new HashMap[String, Function[String, MedRes]]()

  map.put("video/*", (path: String) => new VdoRes(path))
  map.put("audio/*", (path: String) => new AudRes(path))
  map.put("image/*", (path: String) => new ImgRes(path))

  def create(mimeType: String, path: String): MedRes = {
    val constructor = findConstructor(mimeType)
    if (constructor == null) {
      throw new IllegalArgumentException("Unsupported mime type: " + mimeType)
    }
    constructor.apply(path)
  }

  /**
   * 为一个文件创建所有可用的媒体资源。
   * 视频文件如果包含音频流，会额外创建 AudRes。
   */
  def createAll(mimeType: String, path: String): List[MedRes] = {
    val results: List[MedRes] = new ArrayList[MedRes]()

    val typeWildcard = MimeType.getTypeWildcard(mimeType)
    val constructor = findConstructor(mimeType)

    if (constructor == null) {
      throw new IllegalArgumentException("Unsupported mime type: " + mimeType)
    }

    // 主资源
    results.add(constructor.apply(path))

    // 视频文件如果包含音频流，额外创建 AudRes
    if (typeWildcard.equals("video/*") && hasAudioStream(path)) {
      results.add(new AudRes(path))
    }

    results
  }

  private def findConstructor(mimeType: String): Function[String, MedRes] = {
    val constructor = map.get(mimeType)
    if (constructor != null) {
      return constructor
    }

    val typeWildcard = MimeType.getTypeWildcard(mimeType)
    map.get(typeWildcard)
  }
  def isSupported(mimeType: String): Boolean = {
    if (mimeType == null) {
      return false
    }
    findConstructor(mimeType) != null
  }
  def register(mimeType: String, constructor: Function[String, MedRes]): Unit = {
    map.put(mimeType, constructor)
  }
  def unregister(mimeType: String): Unit = {
    map.remove(mimeType)
  }
}

object MediaFactory {
  /**
   * 轻量探测文件是否包含音频流
   */
  private def hasAudioStream(path: String): Boolean = {
    val g = new FFmpegFrameGrabber(path)
    try {
      g.start()
      g.getAudioChannels() > 0
    } catch {
      case e: Exception => false
    } finally {
      if (g != null) {
        g.close()
      }
    }
  }
}
