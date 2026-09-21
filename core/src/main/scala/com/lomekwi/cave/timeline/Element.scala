package com.lomekwi.cave.timeline

import com.lomekwi.cave.pipeline.Source

import java.io.Serializable

/**
 * 轨道元素，一个ADT。轨道被元素完整划分：任意时刻恰好由一个元素占据。
 * 元素不持有区间，区间与元素的对应由 {@link Track} 维护。
 */
sealed trait Element extends Serializable

/** 普通片段，包裹一个管线源。 */
@SerialVersionUID(1L)
final case class Segment(source: Source[?]) extends Element

/**
 * 空隙。每个占位区间一个独立实例，因此不能用 case：
 * 它的相等必须是身份相等，否则无法充当"元素到区间"那张反向表的键。
 */
@SerialVersionUID(1L)
final class Gap extends Element
