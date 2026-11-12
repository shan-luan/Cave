package com.lomekwi.cave.timeline

import com.lomekwi.cave.pipeline.{Filter, FilterChain, Frame, Node, Source}
import com.lomekwi.cave.project.{Id, IdSource}
import com.lomekwi.cave.ui.editpanel.inspector.SourceActor
import com.lomekwi.cave.ui.editpanel.tlarea.TlSegmentActor
import com.lomekwi.cave.util.i18n.*
import com.lomekwi.cave.util.i18n.BasicLang.*

import java.io.Serializable

/**
 * 轨道元素
 *
 */
sealed trait Element extends Serializable

/**
 * 片段。
 */
sealed trait Segment extends Element {
  val id: Id
  val source: Source[? <: Frame]
  val chain: FilterChain

  final def filters: Vector[Filter[?]] = chain.filters

  final def filterChain: FilterChain = chain

  def withFilterChain(next: FilterChain): Segment

  // 只换 id，source 和 chain 共享；跨轨道同时播放会共用 Source 运行时。
  def withId(next: Id): Segment

  final def addFilter(filter: Filter[?])(using ids: IdSource): Segment = withFilterChain(chain.append(filter))

  final def removeFilter(filter: Filter[?]): Segment = withFilterChain(chain.remove(filter))

  final def insertFilter(index: Int, filter: Filter[?])(using ids: IdSource): Segment = withFilterChain(chain.insert(index, filter))

  final def replaceFilter(index: Int, filter: Filter[?]): Segment = withFilterChain(chain.replace(index, filter))

  final def moveFilter(filter: Filter[?], toIndex: Int): Segment = withFilterChain(chain.move(filter, toIndex))

  final def clearFilters(): Segment = withFilterChain(chain.clear)

  final def setFilterDefault(port: Node.InPort[?], value: Any): Segment = withFilterChain(chain.setDefault(port, value))

  final def setFilterDefault(filterId: Id, portIndex: Int, value: Any): Segment = withFilterChain(chain.setDefault(filterId, portIndex, value))

  final def defaultOf(port: Node.InPort[?]): Any = chain.defaultOf(port)

  @transient private lazy val segmentActor: TlSegmentActor = source.createTlSegmentActor(this)

  /**
   * 获取指定时间的产品。
   */
  final def get(time: Timespan, track: Track): Frame = source.synchronized {
    val generated = source.generate(time, track, this)
    if (chain.isEmpty) generated else chain.lastFilter.filterOut.getData(chain).asInstanceOf[Frame]
  }

  /**
   * 同步到指定时间，与 [[get]] 同锁。
   * @param time 片段内时间
   */
  def sync(time: Timespan, track: Track): Unit = source.synchronized {
    source.sync(time, track, this)
  }

  def prefetch(): Unit = {
    source.prefetch()
  }

  /** 挂载滤镜。滤镜处理的帧类型必须接受本片段源的产出类型，否则抛 [[IllegalArgumentException]]。 */
  def attach(filter: Filter[?])(using ids: IdSource): Segment = {
    require(filter.getType.isAssignableFrom(source.getType),
      ("滤镜的帧类型与本片段源的产出类型不兼容: ".in(Zh) | "The filter's frame type is incompatible with the output type of this segment's source: ".in(En)).str() + filter.name)
    addFilter(filter)
  }

  def getType: Class[? <: Frame] = {
    source.getType
  }

  def getLengthPerExportFrame: Timespan = {
    source.getLengthPerExportFrame
  }

  /** 片段的总时长（微秒） */
  def getDuration: Timespan = {
    source.getDuration
  }

  /**
   * 插入时间轴时片段使用的默认时长。源未声明默认时长时在此抛 [[IllegalStateException]]。
   */
  final def getDefaultDuration: Timespan = {
    source.getDefaultDuration.getOrElse(throw new IllegalStateException(("该源没有可放置的默认时长: ".in(Zh) | "This source has no default duration to place: ".in(En)).str() + displayName))
  }

  def displayName: String = {
    source.displayName
  }

  def getSourceActor: SourceActor = {
    new SourceActor(id)
  }

  /** 本片段在时间线上的可视化 actor */
  def getTlSegmentActor: TlSegmentActor = segmentActor
}

/**
 * 内容片段
 * 有界用 [[Clip]]，时长无界的用 [[Boundless]]。
 */
sealed trait Content extends Segment {

  /** 本片段能否与给定内容片段构造转场。 */
  def canCreateTransitionWith(other: Content): Boolean = {
    source.canCreateTransitionWith(other.source)
  }

  /**
   * 以本片段为前段、另一个内容片段为后段，构造转场片段。
   * 转场源由 [[Source.createTransition]] 提供。
   */
  def createTransition(other: Content)(using ids: IdSource): Transition = {
    new Transition(ids.nextId(), source.createTransition(other.source))
  }
}

/**
 * 有界素材内容。origin 是素材 0 秒在时间轴上的位置，前边缘最多回退到素材起点。
 */
@SerialVersionUID(1L)
case class Clip(id: Id, source: Source[? <: Frame], chain: FilterChain) extends Content {
  def this(id: Id, source: Source[? <: Frame]) = this(id, source, FilterChain.empty(source))

  require(source.getDuration != Timespan.MaxValue,
    ("有界片段的源时长必须有限: ".in(Zh) | "The source duration of a bounded segment must be finite: ".in(En)).str() + source.displayName)

  override def withFilterChain(next: FilterChain): Clip = copy(chain = next)

  override def withId(next: Id): Clip = copy(id = next)
}

/**
 * 时长无界的内容，没有素材起点。origin 仅作内时间锚，前边缘不受它约束。
 */
@SerialVersionUID(1L)
case class Boundless(id: Id, source: Source[? <: Frame], chain: FilterChain) extends Content {
  def this(id: Id, source: Source[? <: Frame]) = this(id, source, FilterChain.empty(source))

  require(source.getDuration == Timespan.MaxValue,
    ("无界片段的源时长必须无界: ".in(Zh) | "The source duration of an unbounded segment must be unbounded: ".in(En)).str() + source.displayName)

  override def withFilterChain(next: FilterChain): Boundless = copy(chain = next)

  override def withId(next: Id): Boundless = copy(id = next)
}

/**
 * 转场。一种特殊的片段。对其来说的障碍与 [[Content]] 看到的障碍不同。
 */
@SerialVersionUID(1L)
case class Transition(id: Id, source: Source[? <: Frame], chain: FilterChain) extends Segment {
  def this(id: Id, source: Source[? <: Frame]) = this(id, source, FilterChain.empty(source))

  override def withFilterChain(next: FilterChain): Transition = copy(chain = next)

  override def withId(next: Id): Transition = copy(id = next)
}

/**
 * 空隙。随取随建
 */
@SerialVersionUID(1L)
final case class Gap(before: Option[Id], after: Option[Id]) extends Element
