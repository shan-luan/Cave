package com.lomekwi.cave.ui.editpanel.previewarea;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.utils.Scaling;
import com.lomekwi.cave.app.App;
import com.lomekwi.cave.pipeline.Filter;
import com.lomekwi.cave.pipeline.Source;
import com.lomekwi.cave.pipeline.image.ImgFrame;
import com.lomekwi.cave.pipeline.image.TransFilter;
import com.lomekwi.cave.project.Project;
import com.lomekwi.cave.timeline.Segment;
import com.lomekwi.cave.timeline.UndoManager;
import com.lomekwi.cave.timeline.playback.RefreshRequestEvent;
import com.lomekwi.cave.ui.editpanel.detail.TransFilterActor;

import java.util.List;

public class ImgFrameActor extends Image {
    private final ImgFrame imgFrame;
    private boolean selected;
    private static final Color SELECTED_COLOR = new Color(1, 1, 1, 0.8f);

    private boolean dragging;
    private float startStageX, startStageY;
    private float startFilterDx, startFilterDy;
    private float startFrameX, startFrameY;
    private TransFilter dragFilter;
    private final Vector2 tmpVec = new Vector2();

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

                dragFilter = findOrCreateTransformFilter(source);
                startFilterDx = dragFilter.dx();
                startFilterDy = dragFilter.dy();
                startFrameX = imgFrame.getTransform().x;
                startFrameY = imgFrame.getTransform().y;
                startStageX = event.getStageX();
                startStageY = event.getStageY();
                dragging = false;
                return true;
            }

            @Override
            public void touchDragged(InputEvent event, float x, float y, int pointer) {
                if (dragFilter == null) return;
                dragging = true;
                Group parent = (Group) getParent();
                float s = parent.getScaleX();
                float dx = (event.getStageX() - startStageX) / s;
                float dy = (event.getStageY() - startStageY) / s;
                dragFilter.dx(startFilterDx + dx);
                dragFilter.dy(startFilterDy + dy);
                imgFrame.getTransform().x = startFrameX + dx;
                imgFrame.getTransform().y = startFrameY + dy;
                imgFrame.applyTransform();
                if (dragFilter.getActor() instanceof TransFilterActor ta) {
                    ta.syncFromFilter();
                }
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
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
                if (dragFilter != null && !dragging) {
                    Segment segment = imgFrame.getSource() != null ? imgFrame.getSource().getSegment() : null;
                    if (segment != null && segment.getTrack() != null) {
                        var editPanel = App.root.getFrontendEditPanel();
                        if (editPanel != null) {
                            var tlGroup = editPanel.getTlGroup();
                            if (tlGroup != null) {
                                boolean addToSelection = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT);
                                tlGroup.selectSegment(segment, addToSelection);
                            }
                        }
                    }
                }
                dragFilter = null;
                dragging = false;
            }
        });
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

    public ImgFrame getImgFrame() {
        return imgFrame;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        super.draw(batch, parentAlpha);
        if (selected) {
            App.root.getShapeDrawer().rectangle(
                getX(), getY(), getWidth(), getHeight(),
                SELECTED_COLOR, 2f
            );
        }
    }
}
