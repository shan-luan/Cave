package com.lomekwi.cave.ui.editpanel.inspector;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.VisImageButton;
import com.kotcrab.vis.ui.widget.VisTable;
import com.lomekwi.cave.app.App;
import com.lomekwi.cave.pipeline.Filter;
import com.lomekwi.cave.pipeline.Node;
import com.lomekwi.cave.pipeline.Source;
import com.lomekwi.cave.project.Project;
import com.lomekwi.cave.timeline.UndoManager;
import com.lomekwi.cave.timeline.playback.RefreshRequestEvent;
import com.lomekwi.cave.ui.widget.Card;

import java.util.List;

/**
 * 过滤器节点卡：显示 filter 名称，可编辑其数值输入端口（默认值），支持删除、
 * 标题栏上下交换与拖拽重排。类型 → widget 的映射由 {@link CardWidgetsRegistry} 维护。
 */
public final class FilterActor extends Card {
    private final Filter<?> filter;
    private final Source<?> source;
    private Runnable rebuildCallback;
    private boolean dragging;
    private float dragStageY, dragWindowY;

    public FilterActor(Source<?> source, Filter<?> filter) {
        super(filter.getName());
        this.source = source;
        this.filter = filter;
        align(Align.top | Align.left);
        defaults().left();
        addMoveButton(true);
        addMoveButton(false);
        addCloseButton();
        addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                // 按住标题栏区域即可拖动重排
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
        for (Node.InPort<?> in : filter.getInPorts()) {
            Actor widget = CardWidgetsRegistry.createEditor(in, source);
            if (widget == null) continue; // 未注册该端口类型的 widget，不显示
            add(widget).growX().pad(2).row();
        }
    }

    /** 标题栏加上/下移动按钮，用于交换相邻 filter 的顺序。 */
    private void addMoveButton(boolean up) {
        VisImageButton.VisImageButtonStyle style = new VisImageButton.VisImageButtonStyle(
            VisUI.getSkin().get("close-window", VisImageButton.VisImageButtonStyle.class));
        style.imageUp = VisUI.getSkin().getDrawable(up ? "select-up" : "select-down");
        VisImageButton button = new VisImageButton(style);
        getTitleTable().add(button);
        button.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeListener.ChangeEvent event, Actor actor) {
                move(up);
            }
        });
        button.addListener(new ClickListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                event.cancel();
                return true;
            }
        });
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void move(boolean up) {
        if (source == null) return;
        List filters = source.getFilters();
        int index = filters.indexOf(filter);
        if (index < 0) return;
        int target = up ? index - 1 : index + 1;
        if (target < 0 || target >= filters.size()) return;
        filters.remove(index);
        filters.add(target, filter);
        Project p = App.root.getFrontendProject();
        if (p != null) {
            p.undoManager.record(new UndoManager.ReorderFilterCommand(source, filter, index, target));
            p.projEventBus.post(RefreshRequestEvent.INSTANCE);
        }
        if (rebuildCallback != null) {
            rebuildCallback.run();
        }
    }

    /** 拖拽结束后，根据卡片在列表中的位置计算目标索引并重排。 */
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
        if (pj != null) {
            pj.undoManager.record(new UndoManager.ReorderFilterCommand(source, filter, myIndex, target));
            pj.projEventBus.post(RefreshRequestEvent.INSTANCE);
        }
    }

    @Override
    public void close() {
        int index = source.getFilters().indexOf(filter);
        if (index < 0) return;
        source.getFilters().remove(filter);
        Project p = App.root.getFrontendProject();
        if (p != null) {
            p.undoManager.record(new UndoManager.RemoveFilterCommand(source, filter, index));
            p.projEventBus.post(RefreshRequestEvent.INSTANCE);
        }
        remove();
        if (rebuildCallback != null) rebuildCallback.run();
    }

    public void setRebuildCallback(Runnable callback) {
        this.rebuildCallback = callback;
    }
}
