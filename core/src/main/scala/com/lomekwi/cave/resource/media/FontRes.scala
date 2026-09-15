package com.lomekwi.cave.resource.media

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator
import com.badlogic.gdx.utils.IntMap
import com.lomekwi.cave.resource.Resource

import java.io.Serializable
import scala.compiletime.uninitialized


@SerialVersionUID(1L)
class FontRes(private val path: String) extends Resource with Serializable {
  @transient private var generator: FreeTypeFontGenerator = uninitialized
  @transient private lazy val fontCache: IntMap[BitmapFont] = new IntMap[BitmapFont]()

  def getFont(size: Int): BitmapFont = {
    Option(fontCache.get(size)).getOrElse {
      val param = new FreeTypeFontGenerator.FreeTypeFontParameter()
      param.size = size
      param.incremental = true
      val font = requireGenerator().generateFont(param)
      fontCache.put(size, font)
      font
    }
  }

  private def requireGenerator(): FreeTypeFontGenerator = {
    if (generator == null) {
      var handle = Gdx.files.internal(path)
      if (!handle.exists()) {
        handle = Gdx.files.absolute(path)
      }
      generator = new FreeTypeFontGenerator(handle)
    }
    generator
  }

  def getPath: String = {
    path
  }

  override def close(): Unit = {
    val fontIt = fontCache.values().iterator()
    while (fontIt.hasNext) {
      fontIt.next().dispose()
    }
    fontCache.clear()
    if (generator != null) {
      generator.dispose()
      generator = null
    }
  }
}
