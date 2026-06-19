package com.lomekwi.cave.resource.media;

import com.badlogic.gdx.graphics.Texture;

public interface Previewable {
    void ensureVisible(long startTime, long endTime);
    Texture getPreview(long time);
    long getPreviewInterval();
}
