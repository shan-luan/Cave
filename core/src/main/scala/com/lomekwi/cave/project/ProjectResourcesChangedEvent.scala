package com.lomekwi.cave.project

import java.io.File

case class ProjectResourcesChangedEvent(added: Set[File], removed: Set[File])
