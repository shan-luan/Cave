package com.lomekwi.cave.project

import com.badlogic.gdx.files.FileHandle
import com.lomekwi.cave.resource.Resource
import com.lomekwi.cave.timeline.Timeline
import com.lomekwi.cave.util.FileNameUtil
import com.lomekwi.cave.util.i18n.*
import com.lomekwi.cave.util.i18n.BasicLang.*

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.util.UUID

import scala.util.Using

// 结构相等会深比较 timeline/resources，避免当 Map key 或频繁 ==。
@SerialVersionUID(3L)
final case class Project(name: String,
                         timeline: Timeline,
                         resources: Map[File, Vector[Resource]],
                         uuid: UUID) {

  def withName(name: String): Project = copy(name = name)

  def withTimeline(timeline: Timeline): Project = copy(timeline = timeline)

  def withResources(resources: Map[File, Vector[Resource]]): Project = copy(resources = resources)

  lazy val maxId: Id = timeline.maxId
}

object Project {
  private val PROJECT_EXTENSION = ".cave"

  def create(): Project = Project(
    ("未命名".in(Zh) | "Untitled".in(En)).str(),
    Timeline.empty,
    Map.empty[File, Vector[Resource]],
    UUID.randomUUID())

  def open(fileHandle: FileHandle): Project = {
    val ois = new ObjectInputStream(fileHandle.read())
    ois.readObject().asInstanceOf[Project]
  }
  @deprecated
  def dump(project: Project): Project = {
    val baos = new ByteArrayOutputStream()
    Using.resource(new ObjectOutputStream(baos)) { oos =>
      oos.writeObject(project)
    }
    Using.resource(new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray))) { ois =>
      ois.readObject().asInstanceOf[Project]
    }
  }

  def closeResources(project: Project): Unit = {
    for (buffer <- project.resources.values; resource <- buffer) {
      try {
        resource.close()
      } catch {
        case e: Exception =>
          throw new RuntimeException(e)
      }
    }
  }

  def save(session: ProjectSession, fileHandle: FileHandle): Unit = {
    val target = ensureExtension(fileHandle)
    session.markSaved()
    val baos = new ByteArrayOutputStream()
    Using.resource(new ObjectOutputStream(baos)) { oos =>
      oos.writeObject(session.project)
    }
    val data = baos.toByteArray
    Using.resource(target.write(false)) { os =>
      os.write(data)
    }
    session.savePath = Some(target.file.toPath)
    session.bus.post(ProjectDirtyChangedEvent)
  }

  def save(session: ProjectSession): Unit = {
    val path = session.savePath.getOrElse(throw new FileNotFoundException())
    save(session, new FileHandle(path.toFile))
  }

  def hasProjectExtension(name: String): Boolean = {
    name != null && name.toLowerCase().endsWith(PROJECT_EXTENSION)
  }

  def ensureExtension(fileHandle: FileHandle): FileHandle = {
    if (hasProjectExtension(fileHandle.name())) fileHandle
    else new FileHandle(FileNameUtil.ensureExtension(fileHandle.file(), PROJECT_EXTENSION))
  }
}
