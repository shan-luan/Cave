package com.lomekwi.cave.project

import java.util.concurrent.atomic.AtomicLong

final class IdGenerator private (initial: Id) {
  private val current: AtomicLong = new AtomicLong(Id.value(initial))
  def genId: Id = Id(current.addAndGet(1))
}

object IdGenerator {
  def apply(initial: Id): IdGenerator = new IdGenerator(initial)
}
