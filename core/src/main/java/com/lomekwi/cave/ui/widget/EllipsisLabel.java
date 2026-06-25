package com.lomekwi.cave.ui.widget;

import com.kotcrab.vis.ui.widget.Tooltip;
import com.kotcrab.vis.ui.widget.VisLabel;

public class EllipsisLabel extends VisLabel {
    public EllipsisLabel(String text, int maxChars) {
        super(truncate(text, maxChars));
        new Tooltip.Builder(text).target(this).build();
    }

    private static String truncate(String text, int maxChars) {
        if (text == null) return "";
        if (text.length() <= maxChars) return text;
        return text.substring(0, maxChars) + "...";
    }
}
