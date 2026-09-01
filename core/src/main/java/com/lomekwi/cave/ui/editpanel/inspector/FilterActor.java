package com.lomekwi.cave.ui.editpanel.inspector;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.VisImageButton;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisTextArea;
import com.kotcrab.vis.ui.widget.spinner.SimpleFloatSpinnerModel;
import com.kotcrab.vis.ui.widget.spinner.Spinner;
import com.lomekwi.cave.app.App;
import com.lomekwi.cave.pipeline.Filter;
import com.lomekwi.cave.pipeline.Node;
import com.lomekwi.cave.pipeline.Source;
import com.lomekwi.cave.pipeline.num.NumFrame;
import com.lomekwi.cave.project.Project;
import com.lomekwi.cave.timeline.UndoManager;
import com.lomekwi.cave.timeline.playback.RefreshRequestEvent;
import com.lomekwi.cave.ui.widget.Card;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 过滤器节点卡：显示 filter 名称，可编辑其数值输入端口（默认值），支持删除、
 * 标题栏上下交换与拖拽重排。数值端口每帧从模型回显（gizmo、undo 等外部修改后同步）。
 */
public final class FilterActor extends Card {
    private final Filter<?> filter;
    private final Source<?> source;
    private Runnable rebuildCallback;
    private boolean dragging;
    private float dragStageY, dragWindowY;
    /** 每帧把端口当前值回显到 spinner（gizmo、undo 等外部修改后的同步）。 */
    private final List<SpinnerBinding> bindings = new ArrayList<>();

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
            if (PortWidgets.acceptsString(in)) {
                addTextRow(in);
            } else if (PortWidgets.acceptsNumFrame(in)) {
                addNumRow(in);
            }
        }
    }

    private void addNumRow(Node.InPort<?> port) {
        NumFrame defaultData = (NumFrame) port.getDefaultData();
        double cur = defaultData != null ? defaultData.getVal() : 0;
        SimpleFloatSpinnerModel model = new SimpleFloatSpinnerModel(
            (float) cur, -99999, 99999, 1f, 2);
        Spinner spinner = new Spinner("", model);
        spinner.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                double newVal = ((SimpleFloatSpinnerModel) spinner.getModel()).getValue();
                double oldVal = defaultData != null ? defaultData.getVal() : 0;
                if (oldVal == newVal) return;
                Project p = App.root.getFrontendProject();
                if (p != null) {
                    p.undoManager.record(new UndoManager.NumPortValueCommand(port, source, oldVal, newVal));
                    p.projEventBus.post(RefreshRequestEvent.INSTANCE);
                }
                if (defaultData != null) defaultData.setVal(newVal);
            }
        });
        add(new VisLabel(port.getName())).pad(2).left();
        add(spinner).width(90).pad(2).row();
        bindings.add(new SpinnerBinding(port, spinner, (SimpleFloatSpinnerModel) spinner.getModel()));
    }

    /** String 输入端口：绑定可编辑的文本输入区域。 */
    private void addTextRow(Node.InPort<?> port) {
        String defaultValue = (String) port.getDefaultData();
        VisTextArea textArea = new VisTextArea(defaultValue == null ? "" : defaultValue);
        // 撑高 prefHeight，否则 X2 皮肤下 linesShowing 为 0，文字不会绘制
        textArea.setPrefRows(3);
        add(new VisLabel(port.getName())).pad(2).left();
        add(textArea).growX().pad(2).row();
        textArea.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                String newVal = textArea.getText();
                String oldVal = (String) port.getDefaultData();
                if (Objects.equals(oldVal, newVal)) return;
                @SuppressWarnings("unchecked")
                Node.InPort<String> strPort = (Node.InPort<String>) port;
                strPort.setDefaultData(newVal);
                Project p = App.root.getFrontendProject();
                if (p != null) {
                    p.projEventBus.post(RefreshRequestEvent.INSTANCE);
                }
            }
        });
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        // gizmo（TransFrameActor）、undo 等外部修改端口值后，把最新值回显到 spinner。
        // 使用者正在拖动/输入时不覆盖，避免打断编辑。
        for (SpinnerBinding binding : bindings) {
            // 用户正在该字段输入时不覆盖，避免打断编辑
            if (binding.spinner().getTextField().hasKeyboardFocus()) continue;
            NumFrame data = (NumFrame) binding.port().getDefaultData();
            double modelVal = data != null ? data.getVal() : 0;
            float current = binding.model().getValue();
            if (Math.abs(current - modelVal) > 0.005f) {
                binding.model().setValue((float) modelVal, false);
                binding.spinner().getTextField().setText(
                    String.format(java.util.Locale.US, "%.2f", modelVal));
            }
        }
    }

    /** 端口与 spinner 的绑定，用于每帧回显。 */
    private record SpinnerBinding(Node.InPort<?> port, Spinner spinner, SimpleFloatSpinnerModel model) {}

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
