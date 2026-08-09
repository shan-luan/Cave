package com.lomekwi.cave.resource.media;

import com.badlogic.gdx.graphics.Texture;

/**
 * 单张静态展示图接口（如资源池缩略图），与按时间取帧的 Previewable 正交
 */
public interface Showable {
    Texture getPreview();
}
