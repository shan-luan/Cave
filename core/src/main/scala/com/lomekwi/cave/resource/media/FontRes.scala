package com.lomekwi.cave.resource.media

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator
import com.badlogic.gdx.utils.IntMap
import com.lomekwi.cave.resource.Resource

import java.io.Serializable

import scala.jdk.CollectionConverters.*

@SerialVersionUID(1L)
class FontRes(private val path: String) extends Resource with Serializable {
  @transient private var generator: FreeTypeFontGenerator = null
  @transient private var fontCache: IntMap[BitmapFont] = null

  def getFont(size: Int): BitmapFont = {
    if (fontCache == null) {
      fontCache = new IntMap[BitmapFont]()
    }
    val cached = fontCache.get(size)
    if (cached != null) {
      return cached
    }
    if (generator == null) {
      var handle = Gdx.files.internal(path)
      if (!handle.exists()) {
        handle = Gdx.files.absolute(path)
      }
      generator = new FreeTypeFontGenerator(handle)
    }
    val param = new FreeTypeFontGenerator.FreeTypeFontParameter()
    param.size = size
    param.incremental = true
    val font = generator.generateFont(param)
    fontCache.put(size, font)
    font
  }

  def getPath(): String = {
    path
  }

  override def close(): Unit = {
    if (fontCache != null) {
      val fontIt = fontCache.values().iterator()
      while (fontIt.hasNext) {
        fontIt.next().dispose()
      }
      fontCache.clear()
    }
    if (generator != null) {
      generator.dispose()
      generator = null
    }
  }
}
