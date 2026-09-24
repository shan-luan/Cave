package com.lomekwi.cave.resource.media

import com.lomekwi.cave.util.MimeType
import org.bytedeco.javacv.FFmpegFrameGrabber

import scala.collection.mutable

class MediaFactory {
  import MediaFactory.*

  private val constructors: mutable.Map[String, String => MedRes] = mutable.Map(
    "video/*" -> (path => new VdoRes(path)),
    "audio/*" -> (path => new AudRes(path)),
    "image/*" -> (path => new ImgRes(path))
  )

  def create(mimeType: String, path: String): MedRes = {
    val constructor = findConstructor(mimeType).getOrElse {
      throw new IllegalArgumentException("Unsupported mime type: " + mimeType)
    }
    constructor(path)
  }

  /**
   * 为一个文件创建所有可用的媒体资源。
   * 视频文件如果包含音频流，会额外创建 AudRes。
   */
  def createAll(mimeType: String, path: String): List[MedRes] = {
    val typeWildcard = MimeType.getTypeWildcard(mimeType)
    val constructor = findConstructor(mimeType).getOrElse {
      throw new IllegalArgumentException("Unsupported mime type: " + mimeType)
    }

    if (typeWildcard.equals("video/*") && hasAudioStream(path)) {
      List(constructor(path), new AudRes(path))
    } else {
      List(constructor(path))
    }
  }

  private def findConstructor(mimeType: String): Option[String => MedRes] = {
    constructors.get(mimeType).orElse(constructors.get(MimeType.getTypeWildcard(mimeType)))
  }

  def isSupported(mimeType: String): Boolean = {
    mimeType != null && findConstructor(mimeType).isDefined
  }

  def register(mimeType: String, constructor: String => MedRes): Unit = {
    constructors.put(mimeType, constructor)
  }

  def unregister(mimeType: String): Unit = {
    constructors.remove(mimeType)
  }
}

object MediaFactory {
  private def hasAudioStream(path: String): Boolean = {
    val g = new FFmpegFrameGrabber(path)
    try {
      g.start()
      g.getAudioChannels > 0
    } catch {
      case _: Exception => false
    } finally {
      g.close()
    }
  }
}
