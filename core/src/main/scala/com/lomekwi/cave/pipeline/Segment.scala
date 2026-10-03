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
 * 它是 [[Element]] 中承载内容的那一支，内部再分内容与转场。
 * 只关心"这里是不是片段"的调用方匹配 Segment 即可，不必往下看那一层。
 *
 * 片段不参数化帧类型，帧类型只在 Source 与 Filter 层流动。
 */
@SerialVersionUID(1L)
sealed abstract class Segment(val source: Source[? <: Frame])
  extends Element with Serializable with Duplicatable[Segment] {
  final val filters: FilterList = new FilterList(source)
  @transient private lazy val segmentActor: TlSegmentActor = createTlSegmentActor()

  /**
   * 获取指定时间的产品。生成帧后沿 filter 链（端口连接）求值，
   * 返回链上最后一个 filter 的输出；无 filter 时返回原生帧。
   * @param time 片段内时间
   * @return 产品
   */
  final def get(time: Long, track: Track): Frame = {
    val generated = source.generate(time, track, this)
    if (filters.isEmpty) generated        else filters.get(filters.size() - 1).filterOut.getData.asInstanceOf[Frame]
  }

  /**
   * 同步到指定时间
   * @param time 片段内时间
   */
  def sync(time: Long, track: Track): Unit = {
    source.sync(time, track, this)
  }

  /**
   * 播放头离开本片段时调用。自然播放越过片段终点，或 seek 使播放头落到片段区间之外。
   * @param time 片段内时间，即离开时播放头所在的片段内位置
   */
  def onStepOut(time: Long, track: Track): Unit = {
    source.onStepOut(time, track, this)
  }

  def prefetch(): Unit = {
    source.prefetch()
  }


  /** 挂载滤镜。滤镜处理的帧类型必须接受本片段源的产出类型，否则抛 IllegalArgumentException。 */
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
   * 插入时间轴时片段使用的默认时长。时长无界（[[Segment.getDuration]] 为
   * [[Long.MAX_VALUE]]）的片段必须返回有限值。
   */
  def getDefaultDuration: Long = {
    source.getDefaultDuration
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
 * 内容片段，时间线上承载实际素材的那些。不同素材由构造时注入的 [[Source]] 组合而来，
 * 不再需要为此开放继承。
 */
@SerialVersionUID(1L)
class Content(source: Source[? <: Frame]) extends Segment(source) {

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
 * 转场。一种特殊的片段。对其来说的障碍与[[Content]]看到的障碍不同。
 * 转场由相邻内容的重叠区派生，由 [[com.lomekwi.cave.timeline.Track]] 自行维护。
 */
@SerialVersionUID(1L)
abstract class Transition(source: Source[? <: Frame]) extends Segment(source)

/**
 * 轨道元素，一个ADT，是 [[Track.get]] 的返回值。片段是轨道上真实存在的条目，
 * 空隙只是"此处没有片段"的标记，不存储、没有身份。
 *
 * 片段就是承载内容的那一支，空隙是另一支。它和 [[Gap]] 与 [[Segment]]
 * 声明在同一个文件里，是为了让元素保持 sealed，sealed 只认同源文件的直接子类。
 */
sealed trait Element extends Serializable

/**
 * 空隙。轨道上没有片段覆盖的部分，由 [[Track]] 在查询时随取随建，不参与存储；
 * 区间用 [[Track.rangeAt]] 查。
 */
@SerialVersionUID(1L)
final class Gap extends Element
