package com.lomekwi.cave.pipeline

import com.lomekwi.cave.timeline.Track
import com.lomekwi.cave.ui.editpanel.inspector.GeneratorActor
import com.lomekwi.cave.ui.editpanel.tlarea.TlSrcActor
import com.lomekwi.cave.util.Duplicatable

import java.io.Serializable
import java.util
import scala.compiletime.uninitialized

/**
 * 帧源。由 [[Generator]]（帧的产出）与 [[FilterList]]（过滤链）组合而成，
 * 自身只持有这两者并转发对外门面，不参与生成。
 *
 * 它是 [[Element]] 中承载内容的那一支，内部再分内容与转场。
 * 只关心"这里是不是源"的调用方匹配 Source 即可，不必往下看那一层。
 *
 * @tparam T 帧类型
 */
@SerialVersionUID(1L)
sealed abstract class Source[T <: Frame](private val generator: Generator[T])
  extends Element with Serializable with Duplicatable[Source[T]] {
  private final val filters: util.List[Filter[? >: T]] = new FilterList[T](generator)
  @transient private var srcActor: TlSrcActor = uninitialized

  def getGenerator: Generator[T] = {
    generator
  }

  /**
   * 获取指定时间的产品。生成帧后沿 filter 链（端口连接）求值，
   * 返回链上最后一个 filter 的输出；无 filter 时返回原生帧。
   * @param time 源内时间
   * @return 产品
   */
  final def get(time: Long, track: Track): T = {
    val generated = generator.generate(time, track, this)
    if (filters.isEmpty) generated
    else filters.get(filters.size() - 1).getFilterOut.getData.asInstanceOf[T]
  }

  /**
   * 同步到指定时间
   * @param time 源内时间
   */
  def sync(time: Long, track: Track): Unit = {
    generator.sync(time, track)
  }

  /**
   * 播放头离开本源时调用。自然播放越过源终点，或 seek 使播放头落到源区间之外。
   * @param time 源内时间，即离开时播放头所在的源内位置
   */
  def onStepOut(time: Long, track: Track): Unit = {
    generator.onStepOut(time, track)
  }

  def prefetch(): Unit = {
    generator.prefetch()
  }

  def getFilters: util.List[Filter[? >: T]] = {
    filters
  }

  def attach(filter: Filter[? >: T]): Source[T] = {
    filters.add(filter)
    this
  }

  def getType: Class[T] = {
    generator.getType
  }

  def getLengthPerExportFrame: Long = {
    generator.getLengthPerExportFrame
  }

  /** 媒体源的总时长（微秒） */
  def getDuration: Long = {
    generator.getDuration
  }

  /**
   * 插入时间轴时源使用的默认时长。时长无界（[[Source.getDuration]] 为
   * [[Long.MAX_VALUE]]）的源必须返回有限值。
   */
  def getDefaultDuration: Long = {
    generator.getDefaultDuration
  }

  def getDisplayName: String = {
    generator.getDisplayName
  }

  def getGeneratorActor: GeneratorActor = {
    new GeneratorActor(this)
  }

  def createTlSrcActor(): TlSrcActor = {
    generator.createTlSrcActor(this)
  }

  /** 本源在时间线上的可视化 actor，随取随建。 */
  def getTlSrcActor: TlSrcActor = {
    if (srcActor == null) srcActor = createTlSrcActor()
    srcActor
  }

  override def duplicate(): Source[T] = {
    val copy = super[Duplicatable].duplicate()
    copy.getGenerator.onDuplicate(generator)
    copy
  }
}

/**
 * 内容源，时间线上承载实际素材的那些。不同素材由构造时注入的 [[Generator]] 组合而来，
 * 不再需要为此开放继承。
 */
@SerialVersionUID(1L)
class Content[T <: Frame](generator: Generator[T]) extends Source[T](generator)

/**
 * 转场，连接前后两段内容。源之间如何接、能不能接，是拓扑规则，
 * 由关心它的调用方按这个子类型分辨，不关心的调用方看到的是 [[Source]]。
 * WIP.
 */
@SerialVersionUID(1L)
abstract class Transition[T <: Frame](generator: Generator[T]) extends Source[T](generator)

/**
 * 轨道元素，一个ADT。轨道被元素完整划分，任意时刻恰好由一个元素占据。
 * 元素不持有区间，区间与元素的对应由 [[Track]] 维护。
 *
 * 源就是承载内容的那一支，空隙是另一支。它和 [[Gap]] 与 [[Source]]
 * 声明在同一个文件里，是为了让元素保持 sealed，sealed 只认同源文件的直接子类。
 */
sealed trait Element extends Serializable

/**
 * 空隙。每个占位区间一个独立实例，因此不能用 case。
 * 它的相等必须是身份相等，否则无法充当"元素到区间"那张反向表的键。
 */
@SerialVersionUID(1L)
final class Gap extends Element
