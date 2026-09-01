package com.lomekwi.cave.ui.editpanel.inspector;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import com.kotcrab.vis.ui.widget.VisLabel;
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
 * 通用源信息卡：显示源名称、输入端口与信息输出端口（不含参与 filter 链的 FilterOut）。
 */
public final class SourceActor extends Card {
    private final Source<?> source;
    /** 数值端口与 spinner 的绑定，用于每帧回显（undo 等外部修改后同步）。 */
    private final List<SpinnerBinding> bindings = new ArrayList<>();

    public SourceActor(Source<?> source) {
        super(source.getDisplayName());
        this.source = source;
        align(Align.top | Align.left);
        defaults().left();
        for (Node.InPort<?> in : source.getInPorts()) {
            if (PortWidgets.acceptsString(in)) {
                addTextRow(in);
            } else if (PortWidgets.acceptsNumFrame(in)) {
                addNumRow(in);
            }
        }
        for (Node.OutPort<?> out : source.getOutPorts()) {
            if (!PortWidgets.outputsNumFrame(out)) continue; // FilterOut 与未知类型的输出端口不显示
            NumFrame data = (NumFrame) out.getData();
            String value = String.valueOf(data.getVal());
            add(new VisLabel("< " + out.getName() + ": " + value)).pad(2).left().row();
        }
    }

    /** 数值输入端口：绑定可编辑的 spinner，修改写入默认值并记录 undo。 */
    private void addNumRow(Node.InPort<?> port) {
        NumFrame defaultData = (NumFrame) port.getDefaultData();
        double cur = defaultData != null ? defaultData.getVal() : 0;
        SimpleFloatSpinnerModel model = new SimpleFloatSpinnerModel(
            (float) cur, -99999, 99999, 1f, 2);
        Spinner spinner = new Spinner("", model);
        spinner.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
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

    @Override
    public void act(float delta) {
        super.act(delta);
        // undo 等外部修改端口值后，把最新值回显到 spinner；用户正在输入时不覆盖。
        for (SpinnerBinding binding : bindings) {
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

    /** String 输入端口：绑定可编辑的文本输入区域。 */
    private void addTextRow(Node.InPort<?> port) {
        String defaultValue = (String) port.getDefaultData();
        VisTextArea textArea = new VisTextArea(defaultValue == null ? "" : defaultValue);
        // 撑高 prefHeight，否则 X2 皮肤下 linesShowing 为 0，文字不会绘制
        textArea.setPrefRows(3);
        add(new VisLabel(port.getName())).pad(2).left().row();
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
}