package com.lomekwi.cave.project

opaque type Id = Long

object Id:
  private[project] def apply(value: Long): Id = value

  def noId(): Id = -1

  given Ordering[Id] = Ordering.Long

  extension (id: Id)
    def value: Long = id
