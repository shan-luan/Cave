package com.lomekwi.cave.resource.media;

import com.badlogic.gdx.graphics.Texture;

public interface Previewable {
    Texture getPreview(long time);
    long getPreviewInterval();
}
