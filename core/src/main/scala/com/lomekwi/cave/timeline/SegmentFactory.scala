package com.lomekwi.cave.timeline


import com.lomekwi.cave.project.Project
import com.lomekwi.cave.pipeline.{Boundless, Clip, Content}
import com.lomekwi.cave.pipeline.audio.{AudFrame, AudSource}
import com.lomekwi.cave.pipeline.image.{ImgFrame, ImgSource, VdoSource}
import com.lomekwi.cave.resource.Resource
import com.lomekwi.cave.app.App
import com.lomekwi.cave.resource.media.AudRes
import com.lomekwi.cave.resource.media.MediaCreatedEvent
import com.lomekwi.cave.resource.media.ImgRes
import com.lomekwi.cave.resource.media.VdoRes
import com.lomekwi.cave.util.MimeType

import java.io.{File, IOException, ObjectInputStream, Serializable}

import scala.collection.mutable
import scala.compiletime.uninitialized

/**
 * 片段构造厂。按资源类型登记构造器，据此把 [[Resource]] 变成时间线上可用的 [[Content]]。
 */
@SerialVersionUID(1L)
class SegmentFactory(@transient var project: Project) extends Serializable {
  import SegmentFactory.*

  @transient private var constructors: mutable.HashMap[ResourceClass, SegmentCtor] = uninitialized

  this.constructors = mutable.HashMap.empty[ResourceClass, SegmentCtor]
  initDefaultConstructors()

  private def initDefaultConstructors(): Unit = {
    register(classOf[VdoRes], (segment: Resource) => new Clip(new VdoSource(segment.asInstanceOf[VdoRes])))
    register(classOf[AudRes], (segment: Resource) => new Clip(new AudSource(segment.asInstanceOf[AudRes])))
    register(classOf[ImgRes], (segment: Resource) => new Boundless(new ImgSource(segment.asInstanceOf[ImgRes])))
  }
  def register(clazz: ResourceClass, constructor: SegmentCtor): Unit = {
    constructors.put(clazz, constructor)
  }
  def unregister(clazz: ResourceClass): Unit = {
    constructors.remove(clazz)
  }

  /**
   * 获取文件对应的所有片段。
   * 对于同时包含视频和音频流的文件，可能返回多个片段。
   */
  def getAll(file: File): mutable.ArrayBuffer[Content] = {
    val segments = mutable.ArrayBuffer.empty[Content]
    for (resource <- ensureResources(file)) {
      segments += applyUnchecked(constructors.getOrElse(resource.getClass, null), resource)
    }
    segments
  }

  /**
   * 为尚无资源的文件创建并登记媒体资源，返回该文件的全部资源。
   * 对于同时包含视频和音频流的文件，可能返回多个资源。
   */
  def ensureResources(file: File): mutable.ArrayBuffer[Resource] = {
    project.resources.getOrElseUpdate(file, createResources(file))
  }

  private def createResources(file: File): mutable.ArrayBuffer[Resource] = {
    val mimeType = MimeType.detectMimeType(file)
    if (mimeType == null) {
      throw new IOException("无法检测文件MIME类型: " + file.getName)
    }
    val created = mutable.ArrayBuffer.empty[Resource]
    for (medRes <- App.mediaFactory.createAll(mimeType, file.getPath)) {
      created += medRes
      project.projEventBus.post(MediaCreatedEvent(file, medRes))
    }
    created
  }

  /**
   * 获取文件对应的第一个主要片段。
   */
  def get(file: File): Content = {
    getAll(file).head
  }
  private def applyUnchecked[R <: Resource](fn: SegmentCtor, resource: R): Content = {
    fn(resource)
  }

  private def readObject(ois: ObjectInputStream): Unit = {
    ois.defaultReadObject()
    this.constructors = mutable.HashMap.empty[ResourceClass, SegmentCtor]
    initDefaultConstructors()
  }
}

object SegmentFactory {
  /** 资源类型，构造器登记表的键。 */
  private type ResourceClass = Class[? <: Resource]

  /** 由单个资源构造内容片段。 */
  private type SegmentCtor = Resource => Content
}
