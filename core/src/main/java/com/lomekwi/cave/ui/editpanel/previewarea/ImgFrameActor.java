package com.lomekwi.cave.ui.editpanel.previewarea;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Cursor;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.utils.Scaling;
import com.lomekwi.cave.app.App;
import com.lomekwi.cave.pipeline.Filter;
import com.lomekwi.cave.pipeline.Source;
import com.lomekwi.cave.pipeline.image.ImgFrame;
import com.lomekwi.cave.pipeline.image.Transform;
import com.lomekwi.cave.pipeline.image.TransFilter;
import com.lomekwi.cave.project.Project;
import com.lomekwi.cave.timeline.Segment;
import com.lomekwi.cave.timeline.UndoManager;
import com.lomekwi.cave.timeline.playback.RefreshRequestEvent;
import com.lomekwi.cave.ui.editpanel.detail.TransFilterActor;
import space.earlygrey.shapedrawer.ShapeDrawer;

import java.util.ArrayList;
import java.util.List;

public class ImgFrameActor extends Image {
    private static final float MIN_SCALE = 0.01f;
    private static final float MIN_SIZE = 4f;
    public static final float ROTATE_OFFSET_LOCAL = 40f;

    private final ImgFrame imgFrame;
    private boolean selected;

    private TransFilter dragFilter;
    private boolean dragging;
    private float startCanvasX, startCanvasY;
    private float startFilterDx, startFilterDy;
    private float dragCos, dragSin, dragScaleX, dragScaleY;

    protected boolean gizmoDragging;
    protected Gizmo.Handle gizmoHandle;
    protected final Gizmo gizmo = new Gizmo();
    protected float gizmoStartW, gizmoStartH;
    protected float gizmoStartDx, gizmoStartDy;
    protected float gizmoStartScaleX, gizmoStartScaleY;
    protected float gizmoStartRotation;
    protected float gizmoStartAngle;
    protected float gizmoAnchorLocalX, gizmoAnchorLocalY;
    protected float startGizmoCanvasX, startGizmoCanvasY;
    protected float startGizmoLocalX, startGizmoLocalY;
    protected UndoManager.TransFilterState gizmoOldState;

    private final Vector2 dragStagePos = new Vector2();
    private final Vector2 tmp1 = new Vector2();
    private final Vector2 tmp2 = new Vector2();
    private final Vector2 tmp3 = new Vector2();

    private static final float SNAP_THRESHOLD_SCREEN = 10f;
    private float[] myStartBBox;
    private List<float[]> siblingBBoxes;
    private final Vector2 snapAdjust = new Vector2();

    public ImgFrameActor(ImgFrame imgFrame) {
        super(imgFrame.getTexture());
        this.imgFrame = imgFrame;
        setScaling(Scaling.stretch);
        setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.enabled);
        addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                if (button != 0 || pointer != 0) return false;
                Source<?> source = imgFrame.getSource();
                if (source == null) return false;

                var handle = gizmo.hitHandle(event.getStageX(), event.getStageY());
                if (handle != null && selected) {
                    startGizmoDrag(source, handle, event.getStageX(), event.getStageY());
                    return true;
                }

                dragFilter = findOrCreateTransformFilter(source);
                startFilterDx = dragFilter.dx();
                startFilterDy = dragFilter.dy();
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
                if (dragFilter == null) return;
                dragging = true;
                updateDrag(event.getStageX(), event.getStageY());
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                if (gizmoDragging) {
                    finishGizmoDrag();
                    return;
                }
                if (dragFilter != null && dragging) {
                    Project p = App.root.getFrontendProject();
                    if (p != null) {
                        final TransFilter filter = dragFilter;
                        final float oldDx = startFilterDx, oldDy = startFilterDy;
                        final float newDx = filter.dx(), newDy = filter.dy();
                        p.undoManager.record(new UndoManager.UndoableCommand() {
                            @Override
                            public void undo() {
                                filter.dx(oldDx);
                                filter.dy(oldDy);
                                if (filter.getActor() instanceof TransFilterActor ta) ta.syncFromFilter();
                                p.projEventBus.post(RefreshRequestEvent.INSTANCE);
                            }
                            @Override
                            public void redo() {
                                filter.dx(newDx);
                                filter.dy(newDy);
                                if (filter.getActor() instanceof TransFilterActor ta) ta.syncFromFilter();
                                p.projEventBus.post(RefreshRequestEvent.INSTANCE);
                            }
                        });
                        p.projEventBus.post(RefreshRequestEvent.INSTANCE);
                    }
                }
                if (dragFilter != null && !dragging && !gizmoDragging) {
                    Segment segment = imgFrame.getSource() != null ? imgFrame.getSource().getSegment() : null;
                    if (segment != null && segment.getTrack() != null) {
                        var editPanel = App.root.getFrontendEditPanel();
                        if (editPanel != null) {
                            boolean addToSelection = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT);
                            editPanel.getTlGroup().selectSegment(segment, addToSelection);
                        }
                    }
                }
                dragFilter = null;
                dragging = false;
                gizmoDragging = false;
                gizmoHandle = null;
                myStartBBox = null;
                siblingBBoxes = null;
            }

            @Override
            public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
                gizmo.updateCursor(event.getStageX(), event.getStageY());
            }

            @Override
            public boolean mouseMoved(InputEvent event, float x, float y) {
                gizmo.updateCursor(event.getStageX(), event.getStageY());
                return false;
            }
        });
    }

    @Override
    public Actor hit(float x, float y, boolean touchable) {
        if (!touchable || !isTouchable()) return null;
        if (!selected) {
            return (x >= 0 && x < getWidth() && y >= 0 && y < getHeight()) ? this : null;
        }
        float extTop = getHeight() + ROTATE_OFFSET_LOCAL;
        float extBottom = -ROTATE_OFFSET_LOCAL;
        float extLeft = -ROTATE_OFFSET_LOCAL;
        float extRight = getWidth() + ROTATE_OFFSET_LOCAL;
        if (x >= extLeft && x < extRight && y >= extBottom && y < extTop) {
            return this;
        }
        return null;
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

    // FIXME: 手柄锚点和缩放基于本地坐标轴计算，当 dragFilter 有旋转时与视觉轴不匹配。
    //        视觉"向外"拖拽可能映射到本地"朝向锚点"，导致缩放方向相反。
    //        需在画布空间测量锚点→手柄的视觉距离来计算缩放，再映射回本地 scaleX/scaleY。
    protected void startGizmoDrag(Source<?> source, Gizmo.Handle handle, float stageX, float stageY) {
        gizmoHandle = handle;
        gizmoDragging = true;

        dragFilter = findOrCreateTransformFilter(source);
        gizmoStartW = getWidth();
        gizmoStartH = getHeight();
        gizmoStartDx = dragFilter.dx();
        gizmoStartDy = dragFilter.dy();
        gizmoStartScaleX = dragFilter.scaleX();
        gizmoStartScaleY = dragFilter.scaleY();
        gizmoStartRotation = dragFilter.dRotation();
        gizmoOldState = new UndoManager.TransFilterState(
            gizmoStartDx, gizmoStartDy,
            gizmoStartScaleX, gizmoStartScaleY,
            gizmoStartRotation,
            dragFilter.pivotX(), dragFilter.pivotY(),
            dragFilter.flipX(), dragFilter.flipY());

        computeDragContext();

        Actor p = getParent();
        if (p != null) {
            startGizmoCanvasX = (stageX - p.getX()) / p.getScaleX();
            startGizmoCanvasY = (stageY - p.getY()) / p.getScaleY();
        } else {
            startGizmoCanvasX = stageX;
            startGizmoCanvasY = stageY;
        }
        Vector2 gizmoLocalPos = tmp1;
        gizmoLocalPos.set(stageX, stageY);
        stageToLocalCoordinates(gizmoLocalPos);
        startGizmoLocalX = gizmoLocalPos.x;
        startGizmoLocalY = gizmoLocalPos.y;

        if (handle == Gizmo.Handle.ROTATE) {
            Vector2 centerStagePos = tmp1;
            centerStagePos.set(getWidth() / 2f, getHeight() / 2f);
            localToStageCoordinates(centerStagePos);
            gizmoStartAngle = (float) Math.toDegrees(Math.atan2(
                stageY - centerStagePos.y, stageX - centerStagePos.x));
        }

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
    }

    protected void updateGizmoDrag(float stageX, float stageY) {
        if (gizmoHandle == Gizmo.Handle.ROTATE) {
            updateRotateDrag(stageX, stageY);
            return;
        }

        Actor p = getParent();
        float canvasX = p != null ? (stageX - p.getX()) / p.getScaleX() : stageX;
        float canvasY = p != null ? (stageY - p.getY()) / p.getScaleY() : stageY;
        float canvasDeltaX = canvasX - startGizmoCanvasX;
        float canvasDeltaY = canvasY - startGizmoCanvasY;
        float localX = startGizmoLocalX + (canvasDeltaX * dragCos + canvasDeltaY * dragSin) / dragScaleX;
        float localY = startGizmoLocalY + (-canvasDeltaX * dragSin + canvasDeltaY * dragCos) / dragScaleY;

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

        dragFilter.scaleX(newScaleX);
        dragFilter.scaleY(newScaleY);

        float scaleChangeW = newScaleX / gizmoStartScaleX;
        float scaleChangeH = newScaleY / gizmoStartScaleY;

        float compX = gizmoAnchorLocalX * (1f - scaleChangeW);
        float compY = gizmoAnchorLocalY * (1f - scaleChangeH);

        float ddx = (compX * dragCos + compY * dragSin) / dragScaleX;
        float ddy = (-compX * dragSin + compY * dragCos) / dragScaleY;

        dragFilter.dx(gizmoStartDx + ddx);
        dragFilter.dy(gizmoStartDy + ddy);

        applyFilters();
        if (dragFilter.getActor() instanceof TransFilterActor ta) {
            ta.syncFromFilter();
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

        dragFilter.dRotation(gizmoStartRotation + delta);
        applyFilters();
        if (dragFilter.getActor() instanceof TransFilterActor ta) {
            ta.syncFromFilter();
        }
    }

    protected void finishGizmoDrag() {
        Project p = App.root.getFrontendProject();
        if (p != null && dragFilter != null && gizmoOldState != null) {
            TransFilter filter = dragFilter;
            UndoManager.TransFilterState newState = new UndoManager.TransFilterState(
                filter.dx(), filter.dy(),
                filter.scaleX(), filter.scaleY(),
                filter.dRotation(),
                filter.pivotX(), filter.pivotY(),
                filter.flipX(), filter.flipY());
            if (!gizmoOldState.equals(newState)) {
                p.undoManager.record(new UndoManager.TransformFilterCommand(
                    filter, gizmoOldState, newState));
            }
            p.projEventBus.post(RefreshRequestEvent.INSTANCE);
        }
        gizmoDragging = false;
        gizmoHandle = null;
        dragFilter = null;
        gizmoOldState = null;
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        if (dragFilter == null || getParent() == null || getStage() == null) return;
        dragStagePos.set(Gdx.input.getX(), Gdx.input.getY());
        getStage().screenToStageCoordinates(dragStagePos);
        if (gizmoDragging) {
            updateGizmoDrag(dragStagePos.x, dragStagePos.y);
        } else if (dragging) {
            updateDrag(dragStagePos.x, dragStagePos.y);
        }
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
        dragFilter.dx(startFilterDx + localDx);
        dragFilter.dy(startFilterDy + localDy);
        applyFilters();
        if (dragFilter.getActor() instanceof TransFilterActor ta) {
            ta.syncFromFilter();
        }
    }

    @SuppressWarnings({"unchecked"})
    private void applyFilters() {
        Transform t = imgFrame.getTransform();
        t.reset(0, 0, t.width, t.height);
        Source<?> source = imgFrame.getSource();
        if (source != null) {
            for (Filter<?> f : source.getFilters()) {
                ((Filter<? super ImgFrame>) f).filter(imgFrame);
            }
        }
        imgFrame.applyTransform();
    }

    private void computeDragContext() {
        Transform t = new Transform(0, 0, imgFrame.getTransform().width, imgFrame.getTransform().height, 0);
        Source<?> source = imgFrame.getSource();
        if (source != null) {
            for (Filter<?> f : source.getFilters()) {
                if (f == dragFilter) break;
                if (f instanceof TransFilter tf) {
                    t.applyLocal(tf.dx(), tf.dy(), tf.scaleX(), tf.scaleY(),
                        tf.dRotation(), tf.pivotX(), tf.pivotY(),
                        tf.flipX(), tf.flipY());
                }
            }
        }
        dragScaleX = t.getScaleX();
        dragScaleY = t.getScaleY();
        if (dragScaleX < 0.0001f) dragScaleX = 1f;
        if (dragScaleY < 0.0001f) dragScaleY = 1f;
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
                if (child != this && child instanceof ImgFrameActor other) {
                    siblingBBoxes.add(other.computeCanvasBBox());
                }
            }
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
        float bestDistX = threshold, bestDistY = threshold;

        for (float[] s : siblingBBoxes) {
            float d;

            d = s[0] - pl;
            if (Math.abs(d) < bestDistX) { bestDistX = Math.abs(d); bestSnapX = d; }
            d = s[1] - pl;
            if (Math.abs(d) < bestDistX) { bestDistX = Math.abs(d); bestSnapX = d; }
            d = s[1] - pr;
            if (Math.abs(d) < bestDistX) { bestDistX = Math.abs(d); bestSnapX = d; }
            d = s[0] - pr;
            if (Math.abs(d) < bestDistX) { bestDistX = Math.abs(d); bestSnapX = d; }
            d = s[4] - pcx;
            if (Math.abs(d) < bestDistX) { bestDistX = Math.abs(d); bestSnapX = d; }

            d = s[2] - pb;
            if (Math.abs(d) < bestDistY) { bestDistY = Math.abs(d); bestSnapY = d; }
            d = s[3] - pb;
            if (Math.abs(d) < bestDistY) { bestDistY = Math.abs(d); bestSnapY = d; }
            d = s[3] - pt;
            if (Math.abs(d) < bestDistY) { bestDistY = Math.abs(d); bestSnapY = d; }
            d = s[2] - pt;
            if (Math.abs(d) < bestDistY) { bestDistY = Math.abs(d); bestSnapY = d; }
            d = s[5] - pcy;
            if (Math.abs(d) < bestDistY) { bestDistY = Math.abs(d); bestSnapY = d; }
        }

        if (bestDistX < threshold) snapAdjust.x = bestSnapX;
        if (bestDistY < threshold) snapAdjust.y = bestSnapY;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static TransFilter findOrCreateTransformFilter(Source<?> source) {
        List<Filter<?>> filters = (List) source.getFilters();
        for (int i = filters.size() - 1; i >= 0; i--) {
            Filter<?> f = filters.get(i);
            if (f instanceof TransFilter) {
                return (TransFilter) f;
            }
        }
        TransFilter tf = new TransFilter(source, 0, 0, 1, 1, 0, 0, 0, false, false);
        ((Source) source).attach(tf);
        return tf;
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

    @Override
    public void draw(Batch batch, float parentAlpha) {
        super.draw(batch, parentAlpha);
        if (selected) {
            Matrix4 savedTransform = new Matrix4(batch.getTransformMatrix());
            batch.setTransformMatrix(new Matrix4().idt());
            gizmo.draw(gizmoHandle);
            batch.setTransformMatrix(savedTransform);
        }
    }

    protected class Gizmo {
        private enum Handle {
            NW, N, NE, E, SE, S, SW, W, ROTATE
        }

        private static final int HANDLE_COUNT = Handle.values().length;
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
                {w, 0}, {hw, 0}, {0, 0}, {0, hh},
                {hw, h + ROTATE_OFFSET_LOCAL}
            };

            float r2 = HANDLE_HIT_RADIUS * HANDLE_HIT_RADIUS;
            for (int i = 0; i < HANDLE_COUNT; i++) {
                float dx = lx - localPositions[i][0];
                float dy = ly - localPositions[i][1];
                if (dx * dx + dy * dy <= r2) {
                    return Handle.values()[i];
                }
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
            switch (handle) {
                case NW, SE -> Gdx.graphics.setSystemCursor(Cursor.SystemCursor.NWSEResize);
                case NE, SW -> Gdx.graphics.setSystemCursor(Cursor.SystemCursor.NESWResize);
                case N, S -> Gdx.graphics.setSystemCursor(Cursor.SystemCursor.VerticalResize);
                case E, W -> Gdx.graphics.setSystemCursor(Cursor.SystemCursor.HorizontalResize);
                case ROTATE -> Gdx.graphics.setSystemCursor(Cursor.SystemCursor.Hand);
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
            b = localToStageCoordinates(tmp2.set(hw, h + ROTATE_OFFSET_LOCAL));
            sd.line(a.x, a.y, b.x, b.y, ROTATE_COLOR, lineWidth);
            Vector2 rc = localToStageCoordinates(tmp1.set(hw, h + ROTATE_OFFSET_LOCAL));
            sd.filledCircle(rc.x, rc.y, rotateRadius, ROTATE_COLOR);

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
