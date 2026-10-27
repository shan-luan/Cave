package com.lomekwi.cave.timeline

import com.lomekwi.cave.pipeline.{Filter, FilterList, Frame, Source}
import com.lomekwi.cave.project.{Id, IdSource}
import com.lomekwi.cave.ui.editpanel.inspector.SourceActor
import com.lomekwi.cave.ui.editpanel.tlarea.TlSegmentActor
import com.lomekwi.cave.util.Duplicatable
import com.lomekwi.cave.util.i18n.*
import com.lomekwi.cave.util.i18n.BasicLang.*
import com.lomekwi.cave.util.i18n.TransTargetType.given

import java.io.Serializable

/**
 * 轨道元素
 *
 */
sealed trait Element extends Serializable

/**
 * 片段。
 */
@SerialVersionUID(1L)
//TODO：让其（及字段）不可变。
sealed abstract class Segment(val id: Id, val source: Source[? <: Frame])
  extends Element with Serializable with Duplicatable[Segment] {
  final val filters: FilterList = new FilterList(source)
  @transient private lazy val segmentActor: TlSegmentActor = createTlSegmentActor()

  /**
   * 获取指定时间的产品。
   */
  final def get(time: Long, track: Track): Frame = this.synchronized {
    val generated = source.generate(time, track, this)
    if (filters.isEmpty) generated        else filters.last.filterOut.getData.asInstanceOf[Frame]
  }

  /**
   * 同步到指定时间，与 [[get]] 同锁。
   * @param time 片段内时间
   */
  def sync(time: Long, track: Track): Unit = this.synchronized {
    source.sync(time, track, this)
  }

  def prefetch(): Unit = {
    source.prefetch()
  }


  /** 挂载滤镜。滤镜处理的帧类型必须接受本片段源的产出类型，否则抛 [[IllegalArgumentException]]。 */
  def attach(filter: Filter[?]): Segment = {
    require(filter.getType.isAssignableFrom(source.getType),
      ("滤镜的帧类型与本片段源的产出类型不兼容: ".in(Zh) | "The filter's frame type is incompatible with the output type of this segment's source: ".in(En)).str() + filter.name)
    filters += filter
    this
  }

  def getType: Class[? <: Frame] = {
    source.getType
  }

  def getLengthPerExportFrame: Long = {
    source.getLengthPerExportFrame
  }

  /** 片段的总时长（微秒） */
  def getDuration: Long = {
    source.getDuration
  }

  /**
   * 插入时间轴时片段使用的默认时长。源未声明默认时长时在此抛 [[IllegalStateException]]。
   */
  final def getDefaultDuration: Long = {
    source.getDefaultDuration.getOrElse(throw new IllegalStateException(("该源没有可放置的默认时长: ".in(Zh) | "This source has no default duration to place: ".in(En)).str() + displayName))
  }

  def displayName: String = {
    source.displayName
  }

  def getSourceActor: SourceActor = {
    new SourceActor(id)
  }

  private def createTlSegmentActor(): TlSegmentActor = {
    source.createTlSegmentActor(this)
  }

  /** 本片段在时间线上的可视化 actor */
  def getTlSegmentActor: TlSegmentActor = segmentActor

  override def duplicate(): Segment = {
    val copy = super[Duplicatable].duplicate()
    copy.source.onDuplicate(source)
    copy
  }
}

/**
 * 内容片段
 * 有界用 [[Clip]]，时长无界的用 [[Boundless]]。
 */
@SerialVersionUID(1L)
abstract class Content(id: Id, source: Source[? <: Frame]) extends Segment(id, source) {

  /** 本片段能否与给定内容片段构造转场。 */
  def canCreateTransitionWith(other: Content): Boolean = {
    source.canCreateTransitionWith(other.source)
  }

  /**
   * 以本片段为前段、另一个内容片段为后段，构造转场片段。
   * 转场源由 [[Source.createTransition]] 提供。
   */
  def createTransition(other: Content)(using ids: IdSource): Transition = {
    new Transition(ids.nextId(), source.createTransition(other.source)) {}
  }
}

/**
 * 有界素材内容。origin 是素材 0 秒在时间轴上的位置，前边缘最多回退到素材起点。
 */
@SerialVersionUID(1L)
class Clip(id: Id, source: Source[? <: Frame]) extends Content(id, source) {
  require(source.getDuration != Long.MaxValue,
    ("有界片段的源时长必须有限: ".in(Zh) | "The source duration of a bounded segment must be finite: ".in(En)).str() + source.displayName)
}

/**
 * 时长无界的内容，没有素材起点。origin 仅作内时间锚，前边缘不受它约束。
 */
@SerialVersionUID(1L)
class Boundless(id: Id, source: Source[? <: Frame]) extends Content(id, source) {
  require(source.getDuration == Long.MaxValue,
    ("无界片段的源时长必须无界: ".in(Zh) | "The source duration of an unbounded segment must be unbounded: ".in(En)).str() + source.displayName)
}

/**
 * 转场。一种特殊的片段。对其来说的障碍与 [[Content]] 看到的障碍不同。
 */
@SerialVersionUID(1L)
abstract class Transition(id: Id, source: Source[? <: Frame]) extends Segment(id, source)

/**
 * 空隙。随取随建
 */
@SerialVersionUID(1L)
final case class Gap(before: Option[Id], after: Option[Id]) extends Element
