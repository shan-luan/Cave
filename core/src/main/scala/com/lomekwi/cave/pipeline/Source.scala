package com.lomekwi.cave.pipeline

import com.lomekwi.cave.timeline.Track
import com.lomekwi.cave.ui.editpanel.inspector.SourceActor
import com.lomekwi.cave.ui.editpanel.tlarea.TlSrcActor
import com.lomekwi.cave.util.Duplicatable

import java.io.Serializable
import java.util
import scala.compiletime.uninitialized
import scala.reflect.ClassTag

/**
 * 帧源。自身是 filter 链的头，
 * 提供 FilterOut（链起点，输出 generate() 生成的帧）。
 *
 * 它是 {@link Element} 中承载内容的那一支，内部再分内容与转场。
 * 只关心"这里是不是源"的调用方匹配 Source 即可，不必往下看那一层。
 *
 * @tparam T 帧类型
 */
@SerialVersionUID(1L)
sealed abstract class Source[T <: Frame](using ClassTag[T]) extends Filter[T] with Serializable with Duplicatable[Source[T]] with Element {
  @transient protected var frame: T = null.asInstanceOf[T]
  @transient private var srcActor: TlSrcActor = uninitialized

  /** 链头输出端口，输出本源生成的最新帧供第一个 filter 消费。 */
  final val headOut: FilterOut = addOutPort(new FilterOut {
    override def getData: T = {
      frame
    }

    override def getType: Class[? <: T] = {
      classTag.runtimeClass.asInstanceOf[Class[? <: T]]
    }
  })

  private final val filters: util.List[Filter[? >: T]] = new FilterList[T](this)

  /**
   * 同步到指定时间
   * @param time 源内时间
   */
  def sync(time: Long, track: Track): Unit

  /**
   * 获取指定时间的产品。生成帧后沿 filter 链（端口连接）求值，
   * 返回链上最后一个 filter 的输出；无 filter 时返回原生帧。
   * @param time 源内时间
   * @return 产品
   */
  final def get(time: Long, track: Track): T = {
    frame = generate(time, track)
    if (filters.isEmpty) frame
    else filters.get(filters.size() - 1).getFilterOut.getData.asInstanceOf[T]
  }

  /**
   * 播放头离开本源的片段时调用。自然播放越过片段终点，或 seek 使播放头落到片段区间之外。
   * @param time 源内时间，即离开时播放头所在的片段内位置
   */
  def onStepOut(time: Long, track: Track): Unit = {}

  def prefetch(): Unit = {}

  protected def generate(time: Long, track: Track): T

  def getFilters: util.List[Filter[? >: T]] = {
    filters
  }

  def attach(filter: Filter[? >: T]): Source[T] = {
    filters.add(filter)
    this
  }

  def getLengthPerExportFrame: Long
  /** 媒体源的总时长（微秒） */
  def getDuration: Long
  /**
   * 插入时间轴时片段使用的默认时长。时长无界（{@link #getDuration()} 为
   * {@link Long#MAX_VALUE}）的源必须返回有限值。
   */
  def getDefaultDuration: Long = {
    getDuration
  }
  def getDisplayName: String
  def onDuplicate(original: Source[?]): Unit = {
  }
  def getSourceActor: SourceActor = {
    new SourceActor(this)
  }
  def createTlSrcActor(): TlSrcActor

  /** 本源在时间线上的可视化 actor，随取随建。 */
  def getTlSrcActor: TlSrcActor = {
    if (srcActor == null) srcActor = createTlSrcActor()
    srcActor
  }

  override def duplicate(): Source[T] = {
    val copy = super[Duplicatable].duplicate()
    copy.onDuplicate(this)
    copy
  }

  override def getName: String = {
    getDisplayName
  }
}

/**
 * 内容源，时间线上承载实际素材的那些。
 */
@SerialVersionUID(1L)
abstract class Content[T <: Frame](using ClassTag[T]) extends Source[T]

/**
 * 转场，连接前后两段内容。片段之间如何接、能不能接，是拓扑规则，
 * 由关心它的调用方按这个子类型分辨，不关心的调用方看到的是 {@link Source}。
 * WIP.
 */
@SerialVersionUID(1L)
abstract class Transition[T <: Frame](using ClassTag[T]) extends Source[T]

/**
 * 轨道元素，一个ADT。轨道被元素完整划分，任意时刻恰好由一个元素占据。
 * 元素不持有区间，区间与元素的对应由 {@link Track} 维护。
 *
 * 源就是承载内容的那一支，空隙是另一支。它和 {@link Gap} 与 {@link Source}
 * 声明在同一个文件里，是为了让元素保持 sealed，sealed 只认同源文件的直接子类。
 */
sealed trait Element extends Serializable

/**
 * 空隙。每个占位区间一个独立实例，因此不能用 case。
 * 它的相等必须是身份相等，否则无法充当"元素到区间"那张反向表的键。
 */
@SerialVersionUID(1L)
final class Gap extends Element
