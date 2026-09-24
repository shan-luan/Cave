package com.lomekwi.cave.ui.editpanel.tlarea

/** 拖拽部位。FRONT/BEHIND 为边缘裁切，MIDDLE 为整体平移，NONE 表示未在拖拽。 */
enum DragSide {
  case FRONT, BEHIND, MIDDLE, NONE
}
