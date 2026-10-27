package com.lomekwi.cave.project

import java.util.concurrent.atomic.AtomicLong

object TestIds extends IdSource:
  private val current = new AtomicLong(1_000_000L)

  override def nextId(): Id = Id(current.incrementAndGet())
