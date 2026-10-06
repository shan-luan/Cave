package com.lomekwi.cave.pipeline

/**
 * 当前求值时刻的线程内上下文。求值入口在求值前写入时刻，节点求值期间读取；
 * 按线程隔离，各轨道线程的求值互不可见。
 *
 * 未处于求值上下文时读取抛 IllegalStateException。转场源在求值中拉取子片段时，
 * 子片段读到的是转场自身的时刻。
 */
object EvalClock {
  private final case class Context(timeline: Long, segment: Long)

  private final val context: ThreadLocal[Context] = new ThreadLocal[Context]()

  /** 在指定时刻的上下文中执行 body，结束后恢复调用前的上下文。 */
  def withTime[T](timeline: Long, segment: Long)(body: => T): T = {
    val prev: Context = context.get()
    context.set(Context(timeline, segment))
    try body
    finally {
      if (prev == null) context.remove() else context.set(prev)
    }
  }

  /** 当前求值时刻在时间轴上的绝对时间（微秒）。 */
  def timelineTime: Long = current().timeline

  /** 当前求值时刻相对所在片段起点的相对时间（微秒）。 */
  def segmentTime: Long = current().segment

  private def current(): Context = {
    val c: Context = context.get()
    if (c == null) throw new IllegalStateException("当前线程不在求值上下文中，无法获取时间")
    c
  }
}
