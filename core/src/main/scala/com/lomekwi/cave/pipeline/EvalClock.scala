package com.lomekwi.cave.pipeline

import com.lomekwi.cave.timeline.{Instant, Timespan}
import com.lomekwi.cave.util.i18n.*
import com.lomekwi.cave.util.i18n.BasicLang.*

object EvalClock {
  private final case class Context(timeline: Instant, segment: Timespan)

  private val context: ThreadLocal[Context] = new ThreadLocal[Context]()

  /** 在指定时刻的上下文中执行 body。 */
  def withTime[T](timeline: Instant, segment: Timespan)(body: => T): T = {
    val prev: Context = context.get()
    context.set(Context(timeline, segment))
    try body
    finally {
      if (prev == null) context.remove() else context.set(prev)
    }
  }

  /** 当前求值时刻在时间轴上的绝对时间（微秒）。 */
  def timelineTime: Instant = current().timeline

  /** 当前求值时刻相对所在片段起点的相对时间（微秒）。 */
  def segmentTime: Timespan = current().segment

  private def current(): Context = {
    val c: Context = context.get()
    if (c == null) throw new IllegalStateException(("当前线程不在求值上下文中，无法获取时间".in(Zh) | "The current thread is not in an evaluation context and cannot obtain the time".in(En)).str())
    c
  }
}
