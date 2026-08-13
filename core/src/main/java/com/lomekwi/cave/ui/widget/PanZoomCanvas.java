package com.lomekwi.cave.ui.widget;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup;
import com.lomekwi.cave.app.App;
import com.lomekwi.cave.ui.editpanel.tlarea.TlGroup;

/**
 * 可平移/缩放的画布容器：内部持有 canvas Group，滚轮以光标为中心缩放，
 * 键盘焦点获得时按 SCROLL_* 热键平移。缩放由 zoom * baseScale 构成，
 * baseScale 供外部按视口尺寸适配（如预览区），默认 1。
 */
public class PanZoomCanvas extends WidgetGroup {
    private final Group canvas = new Group();
    private float xOffset, yOffset;
    private float zoom = 1f;
    private float baseScale = 1f;
    private final float minZoom, maxZoom;
    private final float moveSpeed;
    private final Vector2 screenPos = new Vector2();

    public PanZoomCanvas() {
        this(0.05f, 30f, 1000f);
    }

    public PanZoomCanvas(float minZoom, float maxZoom, float moveSpeed) {
        this.minZoom = minZoom;
        this.maxZoom = maxZoom;
        this.moveSpeed = moveSpeed;
        addActor(canvas);
    }

    public Group getCanvas() {
        return canvas;
    }

    public float getScale() {
        return zoom * baseScale;
    }

    public float getZoom() {
        return zoom;
    }

    public void setZoom(float zoom) {
        this.zoom = Math.max(minZoom, Math.min(maxZoom, zoom));
        updateCanvas();
    }

    public void setBaseScale(float baseScale) {
        this.baseScale = baseScale;
        updateCanvas();
    }

    public void setPan(float x, float y) {
        xOffset = x;
        yOffset = y;
        updateCanvas();
    }

    public float getXOffset() {
        return xOffset;
    }

    public float getYOffset() {
        return yOffset;
    }

    /**
     * 以屏幕坐标 (stageX, stageY) 为锚点缩放。
     */
    public void zoomAt(float stageX, float stageY, float amountY) {
        float zoomFactor = 1.1f;
        float oldScale = getScale();
        setZoom((float) (zoom * Math.pow(zoomFactor, -amountY)));
        float newScale = getScale();
        if (newScale == oldScale) return;

        screenPos.set(stageX, stageY);
        stageToLocalCoordinates(screenPos);
        xOffset = screenPos.x - (screenPos.x - xOffset) * (newScale / oldScale);
        yOffset = screenPos.y - (screenPos.y - yOffset) * (newScale / oldScale);
        updateCanvas();
    }

    public void resetView() {
        zoom = 1f;
        xOffset = 0;
        yOffset = 0;
        updateCanvas();
    }

    private void updateCanvas() {
        canvas.setPosition(xOffset, yOffset);
        canvas.setScale(getScale());
    }

    @Override
    public void act(float delta) {
        var stage = getStage();
        if (stage != null && getParent() != null && stage.getKeyboardFocus() == getParent()) {
            float speed = moveSpeed * delta / getScale();
            if (App.shortcutManager.isActive(TlGroup.Actions.SCROLL_UP)) yOffset -= speed;
            if (App.shortcutManager.isActive(TlGroup.Actions.SCROLL_DOWN)) yOffset += speed;
            if (App.shortcutManager.isActive(TlGroup.Actions.SCROLL_LEFT)) xOffset += speed;
            if (App.shortcutManager.isActive(TlGroup.Actions.SCROLL_RIGHT)) xOffset -= speed;
        }
        updateCanvas();
        super.act(delta);
    }
}
