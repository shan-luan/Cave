package com.lomekwi.cave.ui.widget

import com.kotcrab.vis.ui.widget.Tooltip
import com.kotcrab.vis.ui.widget.VisLabel

class EllipsisLabel(text: String, maxChars: Int) extends VisLabel(EllipsisLabel.truncate(text, maxChars)) {
  new Tooltip.Builder(text).target(this).build()
}

object EllipsisLabel {
  private def truncate(text: String, maxChars: Int): String = {
    if (text == null) ""
    else if (text.length() <= maxChars) text
    else text.substring(0, maxChars) + "..."
  }
}
