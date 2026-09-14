package com.lomekwi.cave.project

import com.badlogic.gdx.files.FileHandle
import com.lomekwi.cave.util.FileNameUtil

import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

import scala.util.Using

object Projects {
  final val PROJECT_EXTENSION = ".cave"

  def open(fileHandle: FileHandle): Project = {
    val ois = new ObjectInputStream(fileHandle.read())
    val p = ois.readObject().asInstanceOf[Project]
    p.savePath = fileHandle.file().toPath()
    p
  }
  def create(): Project = {
    new Project()
  }
  def save(project: Project, fileHandle: FileHandle): Unit = {
    val target = ensureExtension(fileHandle)
    project.savedVersion = project.currentVersion
    val baos = new ByteArrayOutputStream()
    Using.resource(new ObjectOutputStream(baos)) { oos =>
      oos.writeObject(project)
    }
    val data = baos.toByteArray()
    Using.resource(target.write(false)) { os =>
      os.write(data)
    }
    project.savePath = target.file().toPath()
    project.projEventBus.post(ProjectDirtyChangedEvent.INSTANCE)
  }
  def save(project: Project): Unit = {
    if (project.savePath == null) {
      throw new FileNotFoundException()
    }
    save(project, new FileHandle(project.savePath.toFile()))
  }

  def hasProjectExtension(name: String): Boolean = {
    name != null && name.toLowerCase().endsWith(PROJECT_EXTENSION)
  }

  def ensureExtension(fileHandle: FileHandle): FileHandle = {
    if (hasProjectExtension(fileHandle.name())) fileHandle
    else new FileHandle(FileNameUtil.ensureExtension(fileHandle.file(), PROJECT_EXTENSION))
  }
}
