package com.lomekwi.cave.ui;

import com.badlogic.gdx.graphics.Color;

public final class Colors {
    private Colors() {}

    /** 主强调色 */
    public static final Color ACCENT = new Color(0x1ba1e2ff);
    /** 强调色的浅色变体 */
    public static final Color ACCENT_LIGHT = new Color(0x5ebdecff);
    /** 对齐吸附强调色（时间线吸附线、预览区吸附引导线等） */
    public static final Color SNAP_GUIDE = new Color(0xffa500ff);

    /** 节点编辑器背景 */
    public static final Color NODE_BG = new Color(0x262626ff);
    /** 节点编辑器网格线 */
    public static final Color NODE_GRID = new Color(0x474747ff);

    /** 时间线背景 */
    public static final Color TIMELINE_BG = new Color(0x141414ff);
    /** 时间线上的浅色叠加（有效时间范围、轨道行、刻度线） */
    public static final Color TIMELINE_OVERLAY = new Color(0xffffff0d);
    /** 时间线片段悬停高亮 */
    public static final Color SEGMENT_HOVER = new Color(0xffffff40);

    /** 预览区灰色引导线（坐标轴、预设轮廓） */
    public static final Color PREVIEW_GUIDE = new Color(0x808080b3);
    /** 预览区变换框及 gizmo 手柄的白色描边 */
    public static final Color FRAME_OUTLINE = new Color(0xffffffcc);
}
