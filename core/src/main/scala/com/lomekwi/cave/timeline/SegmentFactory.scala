package com.lomekwi.cave.timeline


import com.lomekwi.cave.pipeline.{Boundless, Clip, Content}
import com.lomekwi.cave.pipeline.audio.{AudSource}
import com.lomekwi.cave.pipeline.image.{ImgSource, VdoSource}
import com.lomekwi.cave.resource.Resource
import com.lomekwi.cave.app.App
import com.lomekwi.cave.project.ProjectContext
import com.lomekwi.cave.resource.media.AudRes
import com.lomekwi.cave.resource.media.MediaCreatedEvent
import com.lomekwi.cave.resource.media.ImgRes
import com.lomekwi.cave.resource.media.VdoRes
import com.lomekwi.cave.util.MimeType

import java.io.{File, IOException}

import scala.collection.mutable

class SegmentFactory(private val context: ProjectContext) {
  import SegmentFactory.*

  private var constructors: mutable.HashMap[ResourceClass, SegmentCtor] = mutable.HashMap.empty[ResourceClass, SegmentCtor]

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

  def getAll(file: File): mutable.ArrayBuffer[Content] = {
    val segments = mutable.ArrayBuffer.empty[Content]
    for (resource <- ensureResources(file)) {
      segments += applyUnchecked(constructors.getOrElse(resource.getClass, null), resource)
    }
    segments
  }

  def ensureResources(file: File): mutable.ArrayBuffer[Resource] = {
    context.project.resources.getOrElseUpdate(file, createResources(file))
  }

  private def createResources(file: File): mutable.ArrayBuffer[Resource] = {
    val mimeType = MimeType.detectMimeType(file)
    if (mimeType == null) {
      throw new IOException("无法检测文件MIME类型: " + file.getName)
    }
    val created = mutable.ArrayBuffer.empty[Resource]
    for (medRes <- App.mediaFactory.createAll(mimeType, file.getPath)) {
      created += medRes
      context.bus.post(MediaCreatedEvent(file, medRes))
    }
    created
  }

  def get(file: File): Content = {
    getAll(file).head
  }
  private def applyUnchecked[R <: Resource](fn: SegmentCtor, resource: R): Content = {
    fn(resource)
  }
}

object SegmentFactory {
  /** 资源类型，构造器登记表的键。 */
  private type ResourceClass = Class[? <: Resource]

  /** 由单个资源构造内容片段。 */
  private type SegmentCtor = Resource => Content
}
