package com.lomekwi.cave.task

import com.badlogic.gdx.utils.Json
import com.badlogic.gdx.utils.JsonValue
import com.lomekwi.cave.util.Units
import scala.compiletime.uninitialized

/**
 * 导出参数数据模型，使用 libGDX [[Json]] 序列化。
 */
class ExportOptions extends Json.Serializable {
  var outputPath: String = uninitialized
  var width: Int = 0
  var height: Int = 0
  var fps: Double = 0
  var bitrate: Int = 0

  def this(outputPath: String, width: Int, height: Int, fps: Double, bitrate: Int) = {
    this()
    this.outputPath = outputPath
    this.width = width
    this.height = height
    this.fps = fps
    this.bitrate = bitrate
  }

  def setFrom(other: ExportOptions): Unit = {
    outputPath = other.outputPath
    width = other.width
    height = other.height
    fps = other.fps
    bitrate = other.bitrate
  }

  /** bitrate Mbps 的便捷 getter/setter（存为 bps） */
  def getBitrateMbps: Double = {
    bitrate / Units.MEGA.toDouble
  }

  private def setBitrateMbps(mbps: Double): Unit = {
    this.bitrate = (mbps * Units.MEGA).toInt
  }

  override def write(json: Json): Unit = {
    json.writeValue("outputPath", outputPath)
    json.writeValue("width", width)
    json.writeValue("height", height)
    json.writeValue("fps", fps)
    json.writeValue("bitrateMbps", getBitrateMbps)
  }

  override def read(json: Json, jsonData: JsonValue): Unit = {
    outputPath = jsonData.getString("outputPath", "")
    width = jsonData.getInt("width", 1920)
    height = jsonData.getInt("height", 1080)
    fps = jsonData.getDouble("fps", 30.0)
    setBitrateMbps(jsonData.getDouble("bitrateMbps", 6.0))
  }
}
