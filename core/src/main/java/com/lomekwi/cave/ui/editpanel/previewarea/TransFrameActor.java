package com.lomekwi.cave.ui.editpanel.previewarea;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Cursor;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.lomekwi.cave.app.selection.Selectable;
import com.lomekwi.cave.app.App;
import com.lomekwi.cave.pipeline.Modifier;
import com.lomekwi.cave.task.ExportOptions;
import com.lomekwi.cave.task.ExportOptionsSet;
import com.lomekwi.cave.pipeline.Frame;
import com.lomekwi.cave.pipeline.Source;
import com.lomekwi.cave.pipeline.image.Transform;
import com.lomekwi.cave.pipeline.image.Transformable;
import com.lomekwi.cave.pipeline.image.TransModifier;
import com.lomekwi.cave.project.Project;
import com.lomekwi.cave.timeline.Segment;
import com.lomekwi.cave.timeline.UndoManager;
import com.lomekwi.cave.timeline.playback.RefreshRequestEvent;
import com.lomekwi.cave.ui.editpanel.inspector.TransModifierActor;
import space.earlygrey.shapedrawer.ShapeDrawer;

import java.util.ArrayList;
import java.util.List;

public class TransFrameActor extends Actor implements Selectable {
    private static final float MIN_SCALE = 0.01f;
    private static final float MIN_SIZE = 4f;
    public static final float ROTATE_OFFSET_LOCAL = 70f;
    private static final Matrix4 tmpMatrix = new Matrix4();
    private static final Matrix4 IDENTITY = new Matrix4().idt();

    private Frame frame;
    private Transformable transformable;

    private boolean selected;

    private TransModifier dragModifier;
    private boolean dragging;
    private float startCanvasX, startCanvasY;
    private float startModifierDx, startModifierDy;
    private float dragCos, dragSin, dragScaleX, dragScaleY;
    private boolean dragFlipX, dragFlipY;

    protected boolean gizmoDragging;
    protected Gizmo.Handle gizmoHandle;
    protected final Gizmo gizmo = new Gizmo();
    protected float gizmoStartW, gizmoStartH;
    protected float gizmoStartDx, gizmoStartDy;
    protected float gizmoStartScaleX, gizmoStartScaleY;
    protected float gizmoStartRotation;
    protected float gizmoStartAngle;
    protected float gizmoAnchorLocalX, gizmoAnchorLocalY;
    protected float gizmoAnchorStageX, gizmoAnchorStageY;
    protected float gizmoCos, gizmoSin;
    protected boolean gizmoFlipX, gizmoFlipY;
    protected UndoManager.TransModifierState gizmoOldState;

    private static final Vector2 dragStagePos = new Vector2();
    private static final Vector2 tmp1 = new Vector2();
    private static final Vector2 tmp2 = new Vector2();
    private static final Vector2 tmp3 = new Vector2();
    private static final Vector2 hitCorner1 = new Vector2();
    private static final Vector2 hitCorner2 = new Vector2();
    private static final Vector2 hitCorner3 = new Vector2();
    private static final Vector2 hitCorner4 = new Vector2();
    private static final Rectangle actorBounds = new Rectangle();
    private static final Rectangle previewBounds = new Rectangle();
    private static final Rectangle intersectBounds = new Rectangle();

    private static final float SNAP_THRESHOLD_SCREEN = 10f;
    private float[] myStartBBox;
    private List<float[]> siblingBBoxes;
    private static final Vector2 snapAdjust = new Vector2();

    /** 移动吸附时的提示线位置（画布坐标），NaN 表示无吸附 */
    private float snapLineX = Float.NaN;
    private float snapLineY = Float.NaN;

    public <T extends Frame & Transformable> TransFrameActor(T frame) {
        this.frame = frame;
        this.transformable = frame;
        addListener(new InputListener() {            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                if (button != 0 || pointer != 0) return false;
                Source<?> source = frame.getSource();
                if (source == null) return false;

                var handle = gizmo.hitHandle(event.getStageX(), event.getStageY());
                if (handle != null && selected) {
                    startGizmoDrag(source, handle, event.getStageX(), event.getStageY());
                    return true;
                }

                dragModifier = findOrCreateTransModifier(source);
                startModifierDx = dragModifier.dx.getFloat();
                startModifierDy = dragModifier.dy.getFloat();
                Actor p = getParent();
                startCanvasX = (event.getStageX() - p.getX()) / p.getScaleX();
                startCanvasY = (event.getStageY() - p.getY()) / p.getScaleY();
                computeDragContext();
                captureSnapData();
                dragging = false;
                return true;
            }

            @Override
            public void touchDragged(InputEvent event, float x, float y, int pointer) {
                if (gizmoDragging) {
                    updateGizmoDrag(event.getStageX(), event.getStageY());
                    return;
                }
                if (dragModifier == null) return;
                dragging = true;
                updateDrag(event.getStageX(), event.getStageY());
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                if (gizmoDragging) {
                    finishGizmoDrag();
                    return;
                }
                if (dragModifier != null && dragging) {
                    Project p = App.root.getFrontendProject();
                    if (p != null) {
                        final TransModifier modifier = dragModifier;
                        final float oldDx = startModifierDx, oldDy = startModifierDy;
                        final float newDx = modifier.dx.getFloat(), newDy = modifier.dy.getFloat();
                        p.undoManager.record(new UndoManager.UndoableCommand() {
                            @Override
                            public void undo() {
                                modifier.dx.set(oldDx);
                                modifier.dy.set(oldDy);
                                if (modifier.getActor() instanceof TransModifierActor ta) ta.syncFromModifier();
                                p.projEventBus.post(RefreshRequestEvent.INSTANCE);
                            }
                            @Override
                            public void redo() {
                                modifier.dx.set(newDx);
                                modifier.dy.set(newDy);
                                if (modifier.getActor() instanceof TransModifierActor ta) ta.syncFromModifier();
                                p.projEventBus.post(RefreshRequestEvent.INSTANCE);
                            }
                        });
                        p.projEventBus.post(RefreshRequestEvent.INSTANCE);
                    }
                }
                if (dragModifier != null && !dragging && !gizmoDragging) {
                    Segment segment = frame.getSource() != null ? frame.getSource().getSegment() : null;
                    if (segment != null && segment.getTrack() != null) {
                        var editPanel = App.root.getFrontendEditPanel();
                        if (editPanel != null) {
                            boolean addToSelection = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT);
                            editPanel.getTlGroup().selectSegment(segment, addToSelection);
                        }
                    }
                }
                dragModifier = null;
                dragging = false;
                gizmoDragging = false;
                gizmoHandle = null;
                myStartBBox = null;
                siblingBBoxes = null;
                snapLineX = Float.NaN;
                snapLineY = Float.NaN;
            }

            @Override
            public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
                gizmo.updateCursor(event.getStageX(), event.getStageY());
            }

            @Override
            public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
                if (gizmo.hoveredHandle != null) {
                    gizmo.hoveredHandle = null;
                    gizmo.setCursor(null);
                }
            }

            @Override
            public boolean mouseMoved(InputEvent event, float x, float y) {
                gizmo.updateCursor(event.getStageX(), event.getStageY());
                return false;
            }
        });
    }
    public <T extends Frame & Transformable> void rebind(T frame) {
        this.frame = frame;
        this.transformable = frame;
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        super.draw(batch, parentAlpha);
        transformable.render(batch);
        if (selected) {
            tmpMatrix.set(batch.getTransformMatrix());
            batch.setTransformMatrix(IDENTITY);
            gizmo.draw(gizmoHandle);
            batch.setTransformMatrix(tmpMatrix);
        }
    }
    @Override
    public void act(float delta) {
        super.act(delta);

        var transform = transformable.getTransform();
        float scaleX = transform.getScaleX();
        float scaleY = transform.getScaleY();
        float w = transformable.getBaseWidth() * scaleX;
        float h = transformable.getBaseHeight() * scaleY;

        setPosition(transform.getX(), transform.getY());
        setSize(w, h);
        setOrigin(w / 2, h / 2);
        setRotation(transform.getRotation());
        setScaleX(transform.isFlipX() ? -1 : 1);
        setScaleY(transform.isFlipY() ? -1 : 1);

        if (dragModifier == null || getParent() == null || getStage() == null) return;
        dragStagePos.set(Gdx.input.getX(), Gdx.input.getY());
        getStage().screenToStageCoordinates(dragStagePos);
        if (gizmoDragging) {
            updateGizmoDrag(dragStagePos.x, dragStagePos.y);
        } else if (dragging) {
            updateDrag(dragStagePos.x, dragStagePos.y);
        }
    }

    @Override
    public Actor hit(float x, float y, boolean touchable) {
        if (!touchable || !isTouchable()) return null;
        return insidePreviewHitRegion(x, y) ? this : null;
    }

    /**
     * 判断事件点(x, y)是否落在「本Actor在stage坐标下的AABB 与 预览区域AABB」的交集内。
     * 仅处理交集内的事件，避免 TransFrameActor 抢夺预览区域之外的事件。
     */
    private boolean insidePreviewHitRegion(float x, float y) {
        float w = getWidth(), h = getHeight();

        float handleOff = 0;
        if (selected) {
            handleOff = ROTATE_OFFSET_LOCAL;
            Actor p = getParent();
            if (p != null) {
                float ps = Math.min(Math.abs(p.getScaleX()), Math.abs(p.getScaleY()));
                if (ps > 0.0001f) handleOff /= ps;
            }
        }

        float extLeft = -handleOff;
        float extRight = w + handleOff;
        float extBottom = -handleOff;
        float extTop = h + handleOff;
        if (x < extLeft || x >= extRight || y < extBottom || y >= extTop) return false;

        Actor parent = getParent();
        Actor grand = parent != null ? parent.getParent() : null;
        if (!(grand instanceof PreviewArea preview)) return true;

        hitCorner1.set(extLeft, extBottom);
        hitCorner2.set(extRight, extBottom);
        hitCorner3.set(extLeft, extTop);
        hitCorner4.set(extRight, extTop);
        localToStageCoordinates(hitCorner1);
        localToStageCoordinates(hitCorner2);
        localToStageCoordinates(hitCorner3);
        localToStageCoordinates(hitCorner4);
        actorBounds.set(Math.min(Math.min(hitCorner1.x, hitCorner2.x), Math.min(hitCorner3.x, hitCorner4.x)),
            Math.min(Math.min(hitCorner1.y, hitCorner2.y), Math.min(hitCorner3.y, hitCorner4.y)),
            Math.max(Math.max(hitCorner1.x, hitCorner2.x), Math.max(hitCorner3.x, hitCorner4.x)) - Math.min(Math.min(hitCorner1.x, hitCorner2.x), Math.min(hitCorner3.x, hitCorner4.x)),
            Math.max(Math.max(hitCorner1.y, hitCorner2.y), Math.max(hitCorner3.y, hitCorner4.y)) - Math.min(Math.min(hitCorner1.y, hitCorner2.y), Math.min(hitCorner3.y, hitCorner4.y)));

        hitCorner1.set(0, 0);
        hitCorner2.set(preview.getWidth(), preview.getHeight());
        preview.localToStageCoordinates(hitCorner1);
        preview.localToStageCoordinates(hitCorner2);
        previewBounds.set(Math.min(hitCorner1.x, hitCorner2.x),
            Math.min(hitCorner1.y, hitCorner2.y),
            Math.abs(hitCorner2.x - hitCorner1.x),
            Math.abs(hitCorner2.y - hitCorner1.y));

        intersectBounds.set(actorBounds);
        intersectBounds.x = Math.max(actorBounds.x, previewBounds.x);
        intersectBounds.y = Math.max(actorBounds.y, previewBounds.y);
        intersectBounds.width = Math.min(actorBounds.x + actorBounds.width, previewBounds.x + previewBounds.width) - intersectBounds.x;
        intersectBounds.height = Math.min(actorBounds.y + actorBounds.height, previewBounds.y + previewBounds.height) - intersectBounds.y;
        if (intersectBounds.width <= 0 || intersectBounds.height <= 0) return false;

        hitCorner1.set(x, y);
        localToStageCoordinates(hitCorner1);
        return intersectBounds.contains(hitCorner1.x, hitCorner1.y);
    }

    private void localToParent(float lx, float ly, Vector2 out) {
        float ox = getOriginX(), oy = getOriginY();
        float rad = (float) Math.toRadians(getRotation());
        float cos = (float) Math.cos(rad);
        float sin = (float) Math.sin(rad);
        float sx = getScaleX(), sy = getScaleY();

        float dx = lx - ox;
        float dy = ly - oy;
        float rx = dx * cos * sx - dy * sin * sy;
        float ry = dx * sin * sx + dy * cos * sy;
        out.x = getX() + ox + rx;
        out.y = getY() + oy + ry;
    }

    private float clampToAnchor(float value, float anchor, Gizmo.Handle handle, boolean isX) {
        return switch (handle) {
            case NW -> isX ? Math.min(value, anchor) : Math.max(value, anchor);
            case NE -> Math.max(value, anchor);
            case SE -> isX ? Math.max(value, anchor) : Math.min(value, anchor);
            case SW -> Math.min(value, anchor);
            case N -> isX ? value : Math.max(value, anchor);
            case S -> isX ? value : Math.min(value, anchor);
            case E -> isX ? Math.max(value, anchor) : value;
            case W -> isX ? Math.min(value, anchor) : value;
            default -> value;
        };
    }

    protected void startGizmoDrag(Source<?> source, Gizmo.Handle handle, float stageX, float stageY) {
        gizmoHandle = handle;
        gizmoDragging = true;

        dragModifier = findOrCreateTransModifier(source);
        gizmoStartW = getWidth();
        gizmoStartH = getHeight();
        gizmoStartDx = dragModifier.dx.getFloat();
        gizmoStartDy = dragModifier.dy.getFloat();
        gizmoStartScaleX = dragModifier.scaleX.getFloat();
        gizmoStartScaleY = dragModifier.scaleY.getFloat();
        gizmoStartRotation = dragModifier.dRotation.getFloat();
        gizmoOldState = new UndoManager.TransModifierState(
            gizmoStartDx, gizmoStartDy,
            gizmoStartScaleX, gizmoStartScaleY,
            gizmoStartRotation,
            dragModifier.flipX(), dragModifier.flipY());

        computeDragContext();

        switch (handle) {
            case NW -> {
                gizmoAnchorLocalX = getWidth();
                gizmoAnchorLocalY = 0;
            }
            case N -> {
                gizmoAnchorLocalX = getWidth() / 2f;
                gizmoAnchorLocalY = 0;
            }
            case NE -> {
                gizmoAnchorLocalX = 0;
                gizmoAnchorLocalY = 0;
            }
            case E -> {
                gizmoAnchorLocalX = 0;
                gizmoAnchorLocalY = getHeight() / 2f;
            }
            case SE -> {
                gizmoAnchorLocalX = 0;
                gizmoAnchorLocalY = getHeight();
            }
            case S -> {
                gizmoAnchorLocalX = getWidth() / 2f;
                gizmoAnchorLocalY = getHeight();
            }
            case SW -> {
                gizmoAnchorLocalX = getWidth();
                gizmoAnchorLocalY = getHeight();
            }
            case W -> {
                gizmoAnchorLocalX = getWidth();
                gizmoAnchorLocalY = getHeight() / 2f;
            }
            case ROTATE -> {}
        }

        // 锚点在 stage 坐标下的固定位置
        tmp1.set(gizmoAnchorLocalX, gizmoAnchorLocalY);
        localToStageCoordinates(tmp1);
        gizmoAnchorStageX = tmp1.x;
        gizmoAnchorStageY = tmp1.y;

        // 总变换（含 dragModifier 自身的旋转/翻转）
        float totalRad = (float) Math.toRadians(getRotation());
        gizmoCos = (float) Math.cos(totalRad);
        gizmoSin = (float) Math.sin(totalRad);
        gizmoFlipX = getScaleX() < 0;
        gizmoFlipY = getScaleY() < 0;

        if (handle == Gizmo.Handle.ROTATE) {
            Vector2 centerStagePos = tmp1;
            centerStagePos.set(getWidth() / 2f, getHeight() / 2f);
            localToStageCoordinates(centerStagePos);
            gizmoStartAngle = (float) Math.toDegrees(Math.atan2(
                stageY - centerStagePos.y, stageX - centerStagePos.x));
        }
    }

    protected void updateGizmoDrag(float stageX, float stageY) {
        if (gizmoHandle == Gizmo.Handle.ROTATE) {
            updateRotateDrag(stageX, stageY);
            return;
        }

        // stage 坐标取差后换算为画布本地 delta（canvas 仅平移+均匀缩放）
        Actor p = getParent();
        float canvasDeltaX = p != null ? (stageX - gizmoAnchorStageX) / p.getScaleX() : stageX - gizmoAnchorStageX;
        float canvasDeltaY = p != null ? (stageY - gizmoAnchorStageY) / p.getScaleY() : stageY - gizmoAnchorStageY;

        // 画布空间 → 本地空间（用含 dragModifier 的总变换）
        float dLocalX = canvasDeltaX * gizmoCos + canvasDeltaY * gizmoSin;
        float dLocalY = -canvasDeltaX * gizmoSin + canvasDeltaY * gizmoCos;
        if (gizmoFlipX) dLocalX = -dLocalX;
        if (gizmoFlipY) dLocalY = -dLocalY;

        float localX = gizmoAnchorLocalX + dLocalX;
        float localY = gizmoAnchorLocalY + dLocalY;

        localX = clampToAnchor(localX, gizmoAnchorLocalX, gizmoHandle, true);
        localY = clampToAnchor(localY, gizmoAnchorLocalY, gizmoHandle, false);

        boolean freeScale = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT);

        float newScaleX = gizmoStartScaleX;
        float newScaleY = gizmoStartScaleY;

        switch (gizmoHandle) {
            case NW, NE, SE, SW -> {
                float anchorX = gizmoAnchorLocalX;
                float anchorY = gizmoAnchorLocalY;
                if (Math.abs(anchorX - localX) < 0.01f) localX = anchorX + 0.01f;
                if (Math.abs(anchorY - localY) < 0.01f) localY = anchorY + 0.01f;
                float scaleW = Math.abs(anchorX - localX) / gizmoStartW;
                float scaleH = Math.abs(anchorY - localY) / gizmoStartH;
                scaleW = Math.max(MIN_SCALE, scaleW);
                scaleH = Math.max(MIN_SCALE, scaleH);
                if (!freeScale) {
                    float s = (float) Math.sqrt(scaleW * scaleH);
                    scaleW = scaleH = s;
                }
                newScaleX = gizmoStartScaleX * scaleW;
                newScaleY = gizmoStartScaleY * scaleH;
            }
            case N, S -> {
                float topY = (gizmoHandle == Gizmo.Handle.N) ? localY : gizmoAnchorLocalY;
                float bottomY = (gizmoHandle == Gizmo.Handle.S) ? localY : gizmoAnchorLocalY;
                float newH = Math.max(MIN_SIZE, Math.abs(topY - bottomY));
                float scaleH1 = newH / gizmoStartH;
                scaleH1 = Math.max(MIN_SCALE, scaleH1);
                newScaleY = gizmoStartScaleY * scaleH1;
            }
            case E, W -> {
                float rightX = (gizmoHandle == Gizmo.Handle.E) ? localX : gizmoAnchorLocalX;
                float leftX = (gizmoHandle == Gizmo.Handle.W) ? localX : gizmoAnchorLocalX;
                float newW = Math.max(MIN_SIZE, Math.abs(rightX - leftX));
                float scaleW1 = newW / gizmoStartW;
                scaleW1 = Math.max(MIN_SCALE, scaleW1);
                newScaleX = gizmoStartScaleX * scaleW1;
            }
            case ROTATE -> {}
        }

        dragModifier.scaleX.set(newScaleX);
        dragModifier.scaleY.set(newScaleY);

        float scaleChangeW = newScaleX / gizmoStartScaleX;
        float scaleChangeH = newScaleY / gizmoStartScaleY;

        // 锚点补偿（画布空间）：锚点相对中心偏移随缩放变化，扣掉中心位移后保持锚点不动
        float halfW = gizmoStartW * 0.5f;
        float halfH = gizmoStartH * 0.5f;
        float compX = (scaleChangeW - 1f) * (halfW - gizmoAnchorLocalX);
        float compY = (scaleChangeH - 1f) * (halfH - gizmoAnchorLocalY);
        float rotCompX = gizmoCos * compX - gizmoSin * compY;
        float rotCompY = gizmoSin * compX + gizmoCos * compY;
        if (gizmoFlipX) rotCompX = -rotCompX;
        if (gizmoFlipY) rotCompY = -rotCompY;
        float posDeltaX = rotCompX - (scaleChangeW - 1f) * halfW;
        float posDeltaY = rotCompY - (scaleChangeH - 1f) * halfH;

        // 画布位移 → dragModifier 本地位移（仅用其之前的变换）
        float ddx = (posDeltaX * dragCos + posDeltaY * dragSin) / dragScaleX;
        float ddy = (-posDeltaX * dragSin + posDeltaY * dragCos) / dragScaleY;
        if (dragFlipX) ddx = -ddx;
        if (dragFlipY) ddy = -ddy;

        dragModifier.dx.set(gizmoStartDx + ddx);
        dragModifier.dy.set(gizmoStartDy + ddy);

        applyModifiers();
        if (dragModifier.getActor() instanceof TransModifierActor ta) {
            ta.syncFromModifier();
        }
    }

    protected void updateRotateDrag(float stageX, float stageY) {
        Vector2 centerStagePos = tmp1;
        centerStagePos.set(getWidth() / 2f, getHeight() / 2f);
        localToStageCoordinates(centerStagePos);
        float currentAngle = (float) Math.toDegrees(Math.atan2(
            stageY - centerStagePos.y, stageX - centerStagePos.x));
        float delta = currentAngle - gizmoStartAngle;

        if (delta > 180) delta -= 360;
        if (delta < -180) delta += 360;

        boolean snap = Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT);
        if (snap) {
            delta = Math.round(delta / 15f) * 15f;
        }

        dragModifier.dRotation.set(gizmoStartRotation + delta);
        applyModifiers();
        if (dragModifier.getActor() instanceof TransModifierActor ta) {
            ta.syncFromModifier();
        }
    }

    protected void finishGizmoDrag() {
        Project p = App.root.getFrontendProject();
        if (p != null && dragModifier != null && gizmoOldState != null) {
            TransModifier modifier = dragModifier;
            UndoManager.TransModifierState newState = new UndoManager.TransModifierState(
                modifier.dx.getFloat(), modifier.dy.getFloat(),
                modifier.scaleX.getFloat(), modifier.scaleY.getFloat(),
                modifier.dRotation.getFloat(),
                modifier.flipX(), modifier.flipY());
            if (!gizmoOldState.equals(newState)) {
                p.undoManager.record(new UndoManager.TransformModifierCommand(
                    modifier, gizmoOldState, newState));
            }
            p.projEventBus.post(RefreshRequestEvent.INSTANCE);
        }
        gizmoDragging = false;
        gizmoHandle = null;
        dragModifier = null;
        gizmoOldState = null;
    }

    private void updateDrag(float stageX, float stageY) {
        Actor parent = getParent();
        float canvasX = (stageX - parent.getX()) / parent.getScaleX();
        float canvasY = (stageY - parent.getY()) / parent.getScaleY();
        float dx = canvasX - startCanvasX;
        float dy = canvasY - startCanvasY;
        computeSnapAdjustment(dx, dy);
        dx += snapAdjust.x;
        dy += snapAdjust.y;
        float localDx = (dx * dragCos + dy * dragSin) / dragScaleX;
        float localDy = (-dx * dragSin + dy * dragCos) / dragScaleY;
        if (dragFlipX) localDx = -localDx;
        if (dragFlipY) localDy = -localDy;
        dragModifier.dx.set(startModifierDx + localDx);
        dragModifier.dy.set(startModifierDy + localDy);
        applyModifiers();
        if (dragModifier.getActor() instanceof TransModifierActor ta) {
            ta.syncFromModifier();
        }
    }

    @SuppressWarnings({"unchecked"})
    private void applyModifiers() {
        transformable.reset();
        Source<?> source = frame.getSource();
        if (source != null) {
            long localTime = frame.timestamp;
            Segment seg = source.getSegment();
            if (seg != null) localTime -= seg.getOrigin();
            for (Modifier<?> m : source.getModifiers()) {
                ((Modifier<? super Transformable>) m).modify(transformable, localTime);
            }
        }
    }

    private void computeDragContext() {
        Transform t = new Transform(0, 0, 0);
        Source<?> source = frame.getSource();
        if (source != null) {
            for (Modifier<?> m : source.getModifiers()) {
                if (m == dragModifier) break;
                if (m instanceof TransModifier tf) {
                    t.applyLocal(tf.dx.getFloat(), tf.dy.getFloat(), tf.scaleX.getFloat(), tf.scaleY.getFloat(),
                        tf.dRotation.getFloat(),
                        tf.flipX(), tf.flipY());
                }
            }
        }
        dragScaleX = t.getScaleX();
        dragScaleY = t.getScaleY();
        if (dragScaleX < 0.0001f) dragScaleX = 1f;
        if (dragScaleY < 0.0001f) dragScaleY = 1f;
        dragFlipX = t.isFlipX();
        dragFlipY = t.isFlipY();
        float rotRad = t.getRotationRadians();
        dragCos = (float) Math.cos(rotRad);
        dragSin = (float) Math.sin(rotRad);
    }

    private void captureSnapData() {
        myStartBBox = computeCanvasBBox();
        siblingBBoxes = new ArrayList<>();
        Actor p = getParent();
        if (p instanceof com.badlogic.gdx.scenes.scene2d.Group g) {
            for (Actor child : g.getChildren()) {
                if (child != this && child instanceof TransFrameActor other) {
                    siblingBBoxes.add(other.computeCanvasBBox());
                }
            }
        }
        ExportOptionsSet set = ExportOptionsSet.load();
        for (ExportOptions opts : set.presets) {
            if (opts.width <= 0 || opts.height <= 0) continue;
            float l = 0, r = opts.width;
            float b = 0, t = opts.height;
            siblingBBoxes.add(new float[]{l, r, b, t, (l + r) * 0.5f, (b + t) * 0.5f});
        }
    }

    private float[] computeCanvasBBox() {
        float w = getWidth(), h = getHeight();
        localToParent(0, 0, tmp1);
        localToParent(w, 0, tmp2);
        float l = Math.min(tmp1.x, tmp2.x);
        float r = Math.max(tmp1.x, tmp2.x);
        float b = Math.min(tmp1.y, tmp2.y);
        float t = Math.max(tmp1.y, tmp2.y);
        localToParent(0, h, tmp3);
        l = Math.min(l, tmp3.x);
        r = Math.max(r, tmp3.x);
        b = Math.min(b, tmp3.y);
        t = Math.max(t, tmp3.y);
        localToParent(w, h, tmp2);
        l = Math.min(l, tmp2.x);
        r = Math.max(r, tmp2.x);
        b = Math.min(b, tmp2.y);
        t = Math.max(t, tmp2.y);
        return new float[]{l, r, b, t, (l + r) * 0.5f, (b + t) * 0.5f};
    }

    private void computeSnapAdjustment(float dx, float dy) {
        snapAdjust.set(0, 0);
        snapLineX = Float.NaN;
        snapLineY = Float.NaN;
        if (myStartBBox == null || siblingBBoxes == null) return;
        Actor p = getParent();
        float threshold = (p != null) ? SNAP_THRESHOLD_SCREEN / p.getScaleX() : SNAP_THRESHOLD_SCREEN;

        float pl = myStartBBox[0] + dx;
        float pr = myStartBBox[1] + dx;
        float pb = myStartBBox[2] + dy;
        float pt = myStartBBox[3] + dy;
        float pcx = myStartBBox[4] + dx;
        float pcy = myStartBBox[5] + dy;

        float bestSnapX = 0, bestSnapY = 0;
        float bestLineX = Float.NaN, bestLineY = Float.NaN;
        float bestDistX = threshold, bestDistY = threshold;

        for (float[] s : siblingBBoxes) {
            float d;

            d = s[0] - pl;
            if (Math.abs(d) < bestDistX) { bestDistX = Math.abs(d); bestSnapX = d; bestLineX = s[0]; }
            d = s[1] - pl;
            if (Math.abs(d) < bestDistX) { bestDistX = Math.abs(d); bestSnapX = d; bestLineX = s[1]; }
            d = s[1] - pr;
            if (Math.abs(d) < bestDistX) { bestDistX = Math.abs(d); bestSnapX = d; bestLineX = s[1]; }
            d = s[0] - pr;
            if (Math.abs(d) < bestDistX) { bestDistX = Math.abs(d); bestSnapX = d; bestLineX = s[0]; }
            d = s[4] - pcx;
            if (Math.abs(d) < bestDistX) { bestDistX = Math.abs(d); bestSnapX = d; bestLineX = s[4]; }

            d = s[2] - pb;
            if (Math.abs(d) < bestDistY) { bestDistY = Math.abs(d); bestSnapY = d; bestLineY = s[2]; }
            d = s[3] - pb;
            if (Math.abs(d) < bestDistY) { bestDistY = Math.abs(d); bestSnapY = d; bestLineY = s[3]; }
            d = s[3] - pt;
            if (Math.abs(d) < bestDistY) { bestDistY = Math.abs(d); bestSnapY = d; bestLineY = s[3]; }
            d = s[2] - pt;
            if (Math.abs(d) < bestDistY) { bestDistY = Math.abs(d); bestSnapY = d; bestLineY = s[2]; }
            d = s[5] - pcy;
            if (Math.abs(d) < bestDistY) { bestDistY = Math.abs(d); bestSnapY = d; bestLineY = s[5]; }
        }

        if (bestDistX < threshold) {
            snapAdjust.x = bestSnapX;
            snapLineX = bestLineX;
        }
        if (bestDistY < threshold) {
            snapAdjust.y = bestSnapY;
            snapLineY = bestLineY;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static TransModifier findOrCreateTransModifier(Source<?> source) {
        List<Modifier<?>> modifiers = (List) source.getModifiers();
        for (int i = modifiers.size() - 1; i >= 0; i--) {
            Modifier<?> f = modifiers.get(i);
            if (f instanceof TransModifier) {
                return (TransModifier) f;
            }
        }
        TransModifier tf = new TransModifier(source, 0, 0, 1, 1, 0, false, false);
        ((Source) source).attach(tf);
        return tf;
    }

    public boolean isSelected() {
        return selected;
    }

    public float getSnapLineX() {
        return snapLineX;
    }

    public float getSnapLineY() {
        return snapLineY;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
        if (!selected) {
            gizmoDragging = false;
            gizmoHandle = null;
            gizmo.hoveredHandle = null;
            if (Gdx.app.getType() == Application.ApplicationType.Desktop) {
                Gdx.graphics.setSystemCursor(Cursor.SystemCursor.Arrow);
            }
        }
    }

    protected class Gizmo {
        protected enum Handle {
            NW, N, NE, E, SE, S, SW, W, ROTATE
        }

        private static final float HANDLE_HIT_RADIUS = 18f;
        private static final Color GIZMO_COLOR = new Color(1f, 1f, 1f, 0.9f);
        private static final Color GIZMO_FILL = new Color(0.2f, 0.6f, 1f, 0.9f);
        private static final Color ROTATE_COLOR = new Color(0.4f, 0.9f, 1f, 0.9f);
        private static final Color SELECTED_COLOR = new Color(1, 1, 1, 0.8f);

        private Handle hoveredHandle;

        private Handle hitHandle(float stageX, float stageY) {
            float w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return null;
            float hw = w / 2f, hh = h / 2f;

            Vector2 localCoords = tmp1;
            localCoords.set(stageX, stageY);
            stageToLocalCoordinates(localCoords);
            float lx = localCoords.x;
            float ly = localCoords.y;

            float[][] localPositions = {
                {0, h}, {hw, h}, {w, h}, {w, hh},
                {w, 0}, {hw, 0}, {0, 0}, {0, hh}
            };

            float r2 = HANDLE_HIT_RADIUS * HANDLE_HIT_RADIUS;
            for (int i = 0; i < 8; i++) {
                float dx = lx - localPositions[i][0];
                float dy = ly - localPositions[i][1];
                if (dx * dx + dy * dy <= r2) {
                    return Handle.values()[i];
                }
            }

            Vector2 anchor = localToStageCoordinates(tmp2.set(hw, h));
            Vector2 refUp = localToStageCoordinates(tmp3.set(hw, h + 1f));
            float dirX = refUp.x - anchor.x;
            float dirY = refUp.y - anchor.y;
            float dirLen = (float) Math.sqrt(dirX * dirX + dirY * dirY);
            if (dirLen < 0.0001f) { dirX = 0; dirY = 1; dirLen = 1; }
            float hx = anchor.x + dirX / dirLen * ROTATE_OFFSET_LOCAL;
            float hy = anchor.y + dirY / dirLen * ROTATE_OFFSET_LOCAL;
            float dx = stageX - hx;
            float dy = stageY - hy;
            if (dx * dx + dy * dy <= r2) {
                return Handle.ROTATE;
            }

            return null;
        }

        private void updateCursor(float stageX, float stageY) {
            if (!selected) return;
            Handle h = hitHandle(stageX, stageY);
            if (h != hoveredHandle) {
                hoveredHandle = h;
                setCursor(h);
            }
        }

        private void setCursor(Handle handle) {
            if (Gdx.app.getType() != Application.ApplicationType.Desktop) return;
            if (handle == null) {
                Gdx.graphics.setSystemCursor(Cursor.SystemCursor.Arrow);
                return;
            }
            if (handle == Handle.ROTATE) {
                Gdx.graphics.setSystemCursor(Cursor.SystemCursor.Hand);
                return;
            }
            // 光标由锚点→手柄的屏幕方向决定，旋转/翻转后仍与视觉一致
            float w = getWidth(), h = getHeight();
            float hx, hy, ax, ay;
            switch (handle) {
                case NW -> { hx = 0; hy = h; ax = w; ay = 0; }
                case N -> { hx = w / 2f; hy = h; ax = w / 2f; ay = 0; }
                case NE -> { hx = w; hy = h; ax = 0; ay = 0; }
                case E -> { hx = w; hy = h / 2f; ax = 0; ay = h / 2f; }
                case SE -> { hx = w; hy = 0; ax = 0; ay = h; }
                case S -> { hx = w / 2f; hy = 0; ax = w / 2f; ay = h; }
                case SW -> { hx = 0; hy = 0; ax = w; ay = h; }
                case W -> { hx = 0; hy = h / 2f; ax = w; ay = h / 2f; }
                default -> { hx = w; hy = 0; ax = 0; ay = h; }
            }
            tmp1.set(ax, ay);
            tmp2.set(hx, hy);
            localToStageCoordinates(tmp1);
            localToStageCoordinates(tmp2);
            float dx = tmp2.x - tmp1.x;
            float dy = tmp2.y - tmp1.y;
            if (handle == Handle.NW || handle == Handle.NE || handle == Handle.SE || handle == Handle.SW) {
                if (dx * dy > 0) {
                    Gdx.graphics.setSystemCursor(Cursor.SystemCursor.NESWResize);
                } else {
                    Gdx.graphics.setSystemCursor(Cursor.SystemCursor.NWSEResize);
                }
            } else if (Math.abs(dx) > Math.abs(dy)) {
                Gdx.graphics.setSystemCursor(Cursor.SystemCursor.HorizontalResize);
            } else {
                Gdx.graphics.setSystemCursor(Cursor.SystemCursor.VerticalResize);
            }
        }

        private void draw(Handle activeHandle) {
            float w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;
            float hw = w / 2f, hh = h / 2f;
            ShapeDrawer sd = App.root.getShapeDrawer();
            float lineWidth = 2f;
            float handleHalf = 6f;
            float rotateRadius = 5f;

            // selection border
            Vector2 bl = localToStageCoordinates(tmp1.set(0, 0));
            Vector2 br = localToStageCoordinates(tmp2.set(w, 0));
            Vector2 tr = localToStageCoordinates(tmp3.set(w, h));
            Vector2 tl = localToStageCoordinates(dragStagePos.set(0, h));
            sd.line(bl.x, bl.y, br.x, br.y, SELECTED_COLOR, 2f);
            sd.line(br.x, br.y, tr.x, tr.y, SELECTED_COLOR, 2f);
            sd.line(tr.x, tr.y, tl.x, tl.y, SELECTED_COLOR, 2f);
            sd.line(tl.x, tl.y, bl.x, bl.y, SELECTED_COLOR, 2f);

            // gizmo lines
            Vector2 a = localToStageCoordinates(tmp1.set(0, 0));
            Vector2 b = localToStageCoordinates(tmp2.set(w, 0));
            sd.line(a.x, a.y, b.x, b.y, GIZMO_COLOR, lineWidth);
            a = localToStageCoordinates(tmp1.set(w, 0));
            b = localToStageCoordinates(tmp2.set(w, h));
            sd.line(a.x, a.y, b.x, b.y, GIZMO_COLOR, lineWidth);
            a = localToStageCoordinates(tmp1.set(w, h));
            b = localToStageCoordinates(tmp2.set(0, h));
            sd.line(a.x, a.y, b.x, b.y, GIZMO_COLOR, lineWidth);
            a = localToStageCoordinates(tmp1.set(0, h));
            b = localToStageCoordinates(tmp2.set(0, 0));
            sd.line(a.x, a.y, b.x, b.y, GIZMO_COLOR, lineWidth);
            a = localToStageCoordinates(tmp1.set(hw, h));
            Vector2 refUp = localToStageCoordinates(tmp2.set(hw, h + 1f));
            float dirX = refUp.x - a.x;
            float dirY = refUp.y - a.y;
            float dirLen = (float) Math.sqrt(dirX * dirX + dirY * dirY);
            if (dirLen < 0.0001f) { dirX = 0; dirY = 1; dirLen = 1; }
            float stickX = a.x + dirX / dirLen * ROTATE_OFFSET_LOCAL;
            float stickY = a.y + dirY / dirLen * ROTATE_OFFSET_LOCAL;
            sd.line(a.x, a.y, stickX, stickY, ROTATE_COLOR, lineWidth);
            sd.filledCircle(stickX, stickY, rotateRadius, ROTATE_COLOR);

            // handles
            for (Handle handle : Handle.values()) {
                if (handle == Handle.ROTATE) continue;
                float hx = switch (handle) {
                    case NW -> 0;
                    case N -> hw;
                    case NE -> w;
                    case E -> w;
                    case SE -> w;
                    case S -> hw;
                    case SW -> 0;
                    case W -> 0;
                    default -> 0;
                };
                float hy = switch (handle) {
                    case NW -> h;
                    case N -> h;
                    case NE -> h;
                    case E -> hh;
                    case SE -> 0;
                    case S -> 0;
                    case SW -> 0;
                    case W -> hh;
                    default -> 0;
                };
                Vector2 hp = localToStageCoordinates(tmp1.set(hx, hy));
                float sx = hp.x;
                float sy = hp.y;
                Color fill = (activeHandle == handle || hoveredHandle == handle)
                    ? GIZMO_COLOR : GIZMO_FILL;
                sd.filledRectangle(sx - handleHalf, sy - handleHalf,
                    handleHalf * 2, handleHalf * 2, fill);
                sd.rectangle(sx - handleHalf, sy - handleHalf,
                    handleHalf * 2, handleHalf * 2, GIZMO_COLOR, 1f);
            }
        }
    }
}
