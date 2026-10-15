package com.lomekwi.cave.project

import com.badlogic.gdx.files.FileHandle
import com.lomekwi.cave.util.FileNameUtil

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

import scala.util.Using

object Projects {
  private final val PROJECT_EXTENSION = ".cave"

  def open(fileHandle: FileHandle): Project = {
    val ois = new ObjectInputStream(fileHandle.read())
    ois.readObject().asInstanceOf[Project]
  }

  def snapshot(project: Project): Project = {
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
  def create(): Project = {
    Project.create()
  }
  def save(context: ProjectContext, fileHandle: FileHandle): Unit = {
    val target = ensureExtension(fileHandle)
    context.markSaved()
    val baos = new ByteArrayOutputStream()
    Using.resource(new ObjectOutputStream(baos)) { oos =>
      oos.writeObject(context.project)
    }
    val data = baos.toByteArray
    Using.resource(target.write(false)) { os =>
      os.write(data)
    }
    context.savePath = target.file.toPath
    context.bus.post(ProjectDirtyChangedEvent)
  }
  def save(context: ProjectContext): Unit = {
    if (context.savePath == null) {
      throw new FileNotFoundException()
    }
    save(context, new FileHandle(context.savePath.toFile))
  }

  def hasProjectExtension(name: String): Boolean = {
    name != null && name.toLowerCase().endsWith(PROJECT_EXTENSION)
  }

  def ensureExtension(fileHandle: FileHandle): FileHandle = {
    if (hasProjectExtension(fileHandle.name())) fileHandle
    else new FileHandle(FileNameUtil.ensureExtension(fileHandle.file(), PROJECT_EXTENSION))
  }
}
