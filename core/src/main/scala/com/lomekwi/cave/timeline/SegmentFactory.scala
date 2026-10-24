package com.lomekwi.cave.timeline

import com.lomekwi.cave.pipeline.audio.AudSource
import com.lomekwi.cave.pipeline.image.{ImgSource, VdoSource}
import com.lomekwi.cave.resource.Resource
import com.lomekwi.cave.app.App
import com.lomekwi.cave.project.ProjectSession
import com.lomekwi.cave.resource.media.AudRes
import com.lomekwi.cave.resource.media.MediaCreatedEvent
import com.lomekwi.cave.resource.media.ImgRes
import com.lomekwi.cave.resource.media.VdoRes
import com.lomekwi.cave.util.MimeType
import com.lomekwi.cave.util.i18n.*
import com.lomekwi.cave.util.i18n.BasicLang.*
import com.lomekwi.cave.util.i18n.TransTargetType.given

import java.io.{File, IOException}

import scala.collection.mutable

class SegmentFactory(private val context: ProjectSession) {
  import SegmentFactory.*

  private val constructors: mutable.HashMap[ResourceClass, SegmentCtor] = mutable.HashMap.empty[ResourceClass, SegmentCtor]

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

  def getAll(file: File): Vector[Content] = {
    ensureResources(file).map { resource =>
      applyUnchecked(constructors.getOrElse(resource.getClass, null), resource)
    }
  }

  def ensureResources(file: File): Vector[Resource] = {
    context.project.resources.getOrElse(file, {
      val created = createResources(file)
      context.applyResources(context.project.resources.updated(file, created))
      created
    })
  }

  private def createResources(file: File): Vector[Resource] = {
    val mimeType = MimeType.detectMimeType(file)
    if (mimeType == null) {
      throw new IOException(("无法检测文件MIME类型: ".in(Zh) | "Cannot detect the MIME type of file: ".in(En)).str() + file.getName)
    }
    val created = App.mediaFactory.createAll(mimeType, file.getPath)
    for (medRes <- created) {
      context.bus.post(MediaCreatedEvent(file, medRes))
    }
    created.toVector
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
