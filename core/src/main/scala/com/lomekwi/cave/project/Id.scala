package com.lomekwi.cave.project

opaque type Id = Long

object Id:
  private[project] def apply(value: Long): Id = value

  /**
   *不需要id的情况用这个。
   */
  def noId(): Id = -1

  given Ordering[Id] = Ordering.Long

  extension (id: Id)
    def value: Long = id
