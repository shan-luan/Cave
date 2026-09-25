package com.lomekwi.cave.timeline


import com.lomekwi.cave.project.Project
import com.lomekwi.cave.pipeline.{Content, Source}
import com.lomekwi.cave.pipeline.audio.{AudFrame, AudGenerator}
import com.lomekwi.cave.pipeline.image.{ImgFrame, ImgGenerator, VdoGenerator}
import com.lomekwi.cave.resource.Resource
import com.lomekwi.cave.app.App
import com.lomekwi.cave.resource.media.AudRes
import com.lomekwi.cave.resource.media.MediaCreatedEvent
import com.lomekwi.cave.resource.media.ImgRes
import com.lomekwi.cave.resource.media.VdoRes
import com.lomekwi.cave.util.MimeType

import java.io.{File, IOException, ObjectInputStream, Serializable}
import java.util
import java.util.function.Function

import scala.compiletime.uninitialized
import scala.jdk.CollectionConverters.*

/**
 * 源构造厂。按资源类型登记构造器，据此把 {@link Resource} 变成时间线上可用的 {@link Source}。
 * 项目中还没有资源的文件先由 {@link App#mediaFactory} 建出资源。
 */
@SerialVersionUID(1L)
class SourceFactory(@transient private var project: Project) extends Serializable {
  import SourceFactory.*

  @transient private var constructors: util.Map[ResourceClass, SourceCtor] = uninitialized

  this.constructors = new util.HashMap[ResourceClass, SourceCtor]()
  initDefaultConstructors()

  def setProject(project: Project): Unit = {
    this.project = project
  }

  private def initDefaultConstructors(): Unit = {
    register(classOf[VdoRes], (source: Resource) => new Content[ImgFrame](new VdoGenerator(source.asInstanceOf[VdoRes])))
    register(classOf[AudRes], (source: Resource) => new Content[AudFrame](new AudGenerator(source.asInstanceOf[AudRes])))
    register(classOf[ImgRes], (source: Resource) => new Content[ImgFrame](new ImgGenerator(source.asInstanceOf[ImgRes])))
  }
  def register(clazz: ResourceClass, constructor: SourceCtor): Unit = {
    constructors.put(clazz, constructor)
  }
  def unregister(clazz: ResourceClass): Unit = {
    constructors.remove(clazz)
  }

  /**
   * 获取文件对应的所有源。
   * 对于同时包含视频和音频流的文件，可能返回多个源。
   */
  def getAll(file: File): util.List[Source[?]] = {
    var existing: util.Collection[Resource] = project.resources.get(file)

    if (existing.isEmpty) {
      val mimeType = MimeType.detectMimeType(file)
      if (mimeType == null) {
        throw new IOException("无法检测文件MIME类型: " + file.getName)
      }

      for (medRes <- App.mediaFactory.createAll(mimeType, file.getPath)) {
        project.resources.put(file, medRes)
        project.projEventBus.post(MediaCreatedEvent(file, medRes))
      }
      existing = project.resources.get(file)
    }

    val sources: util.List[Source[?]] = new util.ArrayList[Source[?]]()
    for (resource <- existing.asScala) {
      sources.add(applyUnchecked(constructors.get(resource.getClass), resource))
    }
    sources
  }

  /**
   * 获取文件对应的第一个主要源。
   */
  def get(file: File): Source[?] = {
    getAll(file).get(0)
  }
  private def applyUnchecked[R <: Resource](fn: SourceCtor, resource: R): Source[?] = {
    fn.asInstanceOf[Function[R, Source[?]]].apply(resource)
  }

  private def readObject(ois: ObjectInputStream): Unit = {
    ois.defaultReadObject()
    this.constructors = new util.HashMap[ResourceClass, SourceCtor]()
    initDefaultConstructors()
  }
}

object SourceFactory {
  /** 资源类型，构造器登记表的键。 */
  private type ResourceClass = Class[? <: Resource]

  /** 由单个资源构造源。 */
  private type SourceCtor = Function[? <: Resource, Source[?]]
}
