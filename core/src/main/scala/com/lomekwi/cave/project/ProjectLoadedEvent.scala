package com.lomekwi.cave.project

class ProjectLoadedEvent(private val newProject: Project) {
  def getNewProject(): Project = {
    newProject
  }
}
