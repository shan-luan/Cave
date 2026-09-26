package com.lomekwi.cave.ui

import com.badlogic.gdx.graphics.Color

object Colors {
  /** 主强调色 */
  final val ACCENT: Color = new Color(0x1ba1e2ff)
  /** 强调色的浅色变体 */
  final val ACCENT_LIGHT: Color = new Color(0x5ebdecff)
  /** 对齐吸附强调色（时间线吸附线、预览区吸附引导线等） */
  final val SNAP_GUIDE: Color = new Color(0xffa500ff)

  /** 节点编辑器背景 */
  final val NODE_BG: Color = new Color(0x262626ff)
  /** 节点编辑器网格线 */
  final val NODE_GRID: Color = new Color(0x474747ff)

  /** 时间线背景 */
  final val TIMELINE_BG: Color = new Color(0x141414ff)
  /** 时间线上的浅色叠加（有效时间范围、轨道行、刻度线） */
  final val TIMELINE_OVERLAY: Color = new Color(0xffffff0d)
  /** 时间线源悬停高亮 */
  final val SRC_HOVER: Color = new Color(0xffffff40)

  /** 预览区灰色引导线（坐标轴、预设轮廓） */
  final val PREVIEW_GUIDE: Color = new Color(0x808080b3)
  /** 预览区变换框及 gizmo 手柄的白色描边 */
  final val FRAME_OUTLINE: Color = new Color(0xffffffcc)
}
