package com.lomekwi.cave.pipeline

import com.lomekwi.cave.timeline.Track
import com.lomekwi.cave.ui.editpanel.inspector.SourceActor
import com.lomekwi.cave.ui.editpanel.tlarea.TlSegmentActor
import com.lomekwi.cave.util.Duplicatable

import java.io.Serializable
import java.util

/**
 * 片段。由 [[Source]]（帧的产出）与 [[FilterList]]（过滤链）组合而成，
 * 自身只持有这两者并转发对外门面，不参与生成。
 *
 * 它是 [[Element]] 中承载内容的那一支，内部再分内容（[[Clip]] 与 [[Boundless]]）与转场。
 * 只关心"这里是不是片段"的调用方匹配 [[Segment]] 即可，不必往下看那一层。
 *
 * 片段不参数化帧类型，帧类型只在 [[Source]] 与 [[Filter]] 层流动。
 */
@SerialVersionUID(1L)
sealed abstract class Segment(val source: Source[? <: Frame])
  extends Element with Serializable with Duplicatable[Segment] {
  final val filters: FilterList = new FilterList(source)
  @transient private lazy val segmentActor: TlSegmentActor = createTlSegmentActor()

  /**
   * 获取指定时间的产品。生成帧后沿 filter 链（端口连接）求值，
   * 返回链上最后一个 filter 的输出；无 filter 时返回原生帧。
   *
   * 求值全程持有本片段的锁，源、解码器与帧槽因此同一时刻只被一个线程使用。
   * 跨轨搬运后旧轨道线程尚未退出时在此排队，不会与新轨道线程并发借用同一解码器。
   * @param time 片段内时间
   * @return 产品
   */
  final def get(time: Long, track: Track): Frame = this.synchronized {
    val generated = source.generate(time, track, this)
    if (filters.isEmpty) generated        else filters.get(filters.size() - 1).filterOut.getData.asInstanceOf[Frame]
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
      "滤镜的帧类型与本片段源的产出类型不兼容: " + filter.name)
    filters.add(filter)
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
    source.getDefaultDuration.getOrElse(throw new IllegalStateException("该源没有可放置的默认时长: " + displayName))
  }

  def displayName: String = {
    source.displayName
  }

  def getSourceActor: SourceActor = {
    new SourceActor(this)
  }

  def createTlSegmentActor(): TlSegmentActor = {
    source.createTlSegmentActor(this)
  }

  /** 本片段在时间线上的可视化 actor，随取随建。 */
  def getTlSegmentActor: TlSegmentActor = segmentActor

  override def duplicate(): Segment = {
    val copy = super[Duplicatable].duplicate()
    copy.source.onDuplicate(source)
    copy
  }
}

/**
 * 内容片段，与 [[Transition]] 相对的可放置用户内容。不同素材由构造时注入的 [[Source]] 组合而来。
 * 有界素材用 [[Clip]]，时长无界的用 [[Boundless]]，源与叶子的配对由两者构造器的 require 保证。
 */
@SerialVersionUID(1L)
abstract class Content(source: Source[? <: Frame]) extends Segment(source) {

  /** 本片段能否与给定内容片段构造转场。 */
  def canCreateTransitionWith(other: Content): Boolean = {
    source.canCreateTransitionWith(other.source)
  }

  /**
   * 以本片段为前段、另一个内容片段为后段，构造转场片段。
   * 转场源由 [[Source.createTransition]] 提供。
   */
  def createTransition(other: Content): Transition = {
    new Transition(source.createTransition(other.source)) {}
  }
}

/**
 * 有界素材内容。origin 是素材 0 秒在时间轴上的位置，前边缘最多回退到素材起点。
 */
@SerialVersionUID(1L)
class Clip(source: Source[? <: Frame]) extends Content(source) {
  require(source.getDuration != Long.MaxValue,
    "有界片段的源时长必须有限: " + source.displayName)
}

/**
 * 时长无界的内容，没有素材起点。origin 仅作内时间锚，前边缘不受它约束。
 */
@SerialVersionUID(1L)
class Boundless(source: Source[? <: Frame]) extends Content(source) {
  require(source.getDuration == Long.MaxValue,
    "无界片段的源时长必须无界: " + source.displayName)
}

/**
 * 转场。一种特殊的片段。对其来说的障碍与 [[Content]] 看到的障碍不同。
 * 转场由相邻内容的重叠区派生，由 [[com.lomekwi.cave.timeline.Track]] 自行维护。
 */
@SerialVersionUID(1L)
abstract class Transition(source: Source[? <: Frame]) extends Segment(source)

/**
 * 轨道元素，一个 ADT，是 [[Track.get]] 的返回值。片段是轨道上真实存在的条目，
 * 空隙只是"此处没有片段"的标记，不存储、没有身份。
 *
 * 片段就是承载内容的那一支，空隙是另一支。
 */
// 与 [[Gap]]、[[Segment]] 声明在同一个文件里，是为了让元素保持 sealed，sealed 只认同源文件的直接子类
sealed trait Element extends Serializable

/**
 * 空隙。轨道上没有片段覆盖的部分，由 [[Track]] 在查询时随取随建，不参与存储；
 * 区间用 [[Track.rangeAt]] 查。
 */
@SerialVersionUID(1L)
final class Gap extends Element
