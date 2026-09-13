package com.lomekwi.cave.timeline


import com.lomekwi.cave.project.Project
import com.lomekwi.cave.pipeline.Source
import com.lomekwi.cave.pipeline.audio.AudClipSrc
import com.lomekwi.cave.pipeline.image.ImgSrc
import com.lomekwi.cave.pipeline.image.VdoClipSrc
import com.lomekwi.cave.resource.Resource
import com.lomekwi.cave.app.App
import com.lomekwi.cave.resource.media.AudRes
import com.lomekwi.cave.resource.media.MediaCreatedEvent
import com.lomekwi.cave.resource.media.MedRes
import com.lomekwi.cave.resource.media.ImgRes
import com.lomekwi.cave.resource.media.VdoRes
import com.lomekwi.cave.util.MimeType

import java.io.{File, IOException, ObjectInputStream, Serializable}
import java.util.{ArrayList, Collection, HashMap, List, Map}
import java.util.function.Function

import scala.jdk.CollectionConverters.*

@SerialVersionUID(1L)
class MediaSegFactory(@transient private var project: Project) extends Serializable {
  @transient private var map: Map[Class[? <: Resource], Function[? <: Resource, Source[?]]] = null

  this.map = new HashMap[Class[? <: Resource], Function[? <: Resource, Source[?]]]()
  initDefaultMappings()

  def setProject(project: Project): Unit = {
    this.project = project
  }

  private def initDefaultMappings(): Unit = {
    register(classOf[VdoRes], (source: Resource) => new VdoClipSrc(source.asInstanceOf[VdoRes]))
    register(classOf[AudRes], (source: Resource) => new AudClipSrc(source.asInstanceOf[AudRes]))
    register(classOf[ImgRes], (source: Resource) => new ImgSrc(source.asInstanceOf[ImgRes]))
  }
  def register(clazz: Class[? <: Resource], constructor: Function[? <: Resource, Source[?]]): Unit = {
    map.put(clazz, constructor)
  }
  def unregister(clazz: Class[? <: Resource]): Unit = {
    map.remove(clazz)
  }

  /**
   * 获取文件对应的所有片段。
   * 对于同时包含视频和音频流的文件，可能返回多个 Segment。
   */
  def getAll(file: File): List[Segment] = {
    var existing: Collection[Resource] = project.resources.get(file)

    if (existing.isEmpty()) {
      val mimeType = MimeType.detectMimeType(file)
      if (mimeType == null) {
        throw new IOException("无法检测文件MIME类型: " + file.getName())
      }

      for (medRes <- App.mediaFactory.createAll(mimeType, file.getPath()).asScala) {
        project.resources.put(file, medRes)
        project.projEventBus.post(new MediaCreatedEvent(file, medRes))
      }
      existing = project.resources.get(file)
    }

    val segments: List[Segment] = new ArrayList[Segment]()
    for (resource <- existing.asScala) {
      segments.add(new Segment(applyUnchecked(map.get(resource.getClass()), resource)))
    }
    segments
  }

  /**
   * 获取文件对应的第一个主要片段（兼容单片段场景）。
   */
  def get(file: File): Segment = {
    getAll(file).get(0)
  }
  private def applyUnchecked[R <: Resource](fn: Function[? <: Resource, Source[?]], resource: R): Source[?] = {
    fn.asInstanceOf[Function[R, Source[?]]].apply(resource)
  }

  private def readObject(ois: ObjectInputStream): Unit = {
    ois.defaultReadObject()
    this.map = new HashMap[Class[? <: Resource], Function[? <: Resource, Source[?]]]()
    initDefaultMappings()
  }
}
