package com.lomekwi.cave.ui.editpanel.detail;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.utils.Align;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisWindow;
import com.lomekwi.cave.app.App;
import com.lomekwi.cave.pipeline.Filter;
import com.lomekwi.cave.pipeline.Source;
import com.lomekwi.cave.project.Project;
import com.lomekwi.cave.timeline.playback.RefreshRequestEvent;

import java.util.List;

public abstract class FilterActor extends VisWindow {
    protected Filter<?> filter;
    protected Source<?> source;
    private Runnable rebuildCallback;
    private boolean dragging;
    private float dragStageY, dragWindowY;

    public FilterActor(String title, Filter<?> filter) {
        super(title);
        this.filter = filter;
        align(Align.top | Align.left);
        defaults().left();
        addCloseButton();
        setMovable(false);
        addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                if (button == 0 && y >= getHeight() - getPadTop()) {
                    dragging = true;
                    dragStageY = event.getStageY();
                    dragWindowY = getY();
                    event.cancel();
                    return true;
                }
                return false;
            }

            @Override
            public void touchDragged(InputEvent event, float x, float y, int pointer) {
                if (!dragging) return;
                event.cancel();
                setY(dragWindowY + (event.getStageY() - dragStageY));
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                if (!dragging) return;
                dragging = false;
                doReorder();
                if (rebuildCallback != null) {
                    rebuildCallback.run();
                }
            }
        });
    }

    public void setSource(Source<?> source) {
        this.source = source;
    }

    public void setRebuildCallback(Runnable callback) {
        this.rebuildCallback = callback;
    }

    @Override
    public void close() {
        filter.invalidateDetailActor();
        source.getFilters().remove(filter);
        Project p = App.root.getFrontendProject();
        if (p != null) p.projEventBus.post(RefreshRequestEvent.INSTANCE);
        remove();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void doReorder() {
        if (source == null) return;
        Actor p = getParent();
        if (!(p instanceof VisTable content)) return;

        List filters = source.getFilters();
        int myIndex = filters.indexOf(filter);
        if (myIndex < 0) return;

        float myCenterY = getY() + getHeight() / 2;
        int target = 0;
        for (Actor child : content.getChildren()) {
            if (!(child instanceof FilterActor) || child == this) continue;
            if (child.getY() + child.getHeight() / 2 > myCenterY) target++;
        }

        if (target == myIndex) return;

        filters.remove(myIndex);
        filters.add(target, filter);

        Project pj = App.root.getFrontendProject();
        if (pj != null) pj.projEventBus.post(RefreshRequestEvent.INSTANCE);
    }
}
