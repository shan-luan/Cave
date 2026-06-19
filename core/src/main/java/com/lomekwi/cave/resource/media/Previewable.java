package com.lomekwi.cave.resource.media;

import com.badlogic.gdx.graphics.Texture;

public interface Previewable {
    void ensureVisible(long startTime, long endTime, long step);
    Texture getPreview(long time);
    long getPreviewInterval();
}
