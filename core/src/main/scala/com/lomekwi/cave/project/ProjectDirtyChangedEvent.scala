package com.lomekwi.cave.project

sealed trait ProjectDirtyChangedEvent

case object ProjectDirtyChangedEvent extends ProjectDirtyChangedEvent
