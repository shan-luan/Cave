package com.lomekwi.cave.ui.editpanel.inspector;

import static com.lomekwi.cave.util.i18n.I18N.i18n;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import com.kotcrab.vis.ui.widget.VisCheckBox;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisSelectBox;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisTextArea;
import com.kotcrab.vis.ui.widget.VisTextButton;
import com.kotcrab.vis.ui.widget.VisTextField;
import com.kotcrab.vis.ui.widget.VisValidatableTextField;
import com.kotcrab.vis.ui.widget.spinner.IntSpinnerModel;
import com.kotcrab.vis.ui.widget.spinner.SimpleFloatSpinnerModel;
import com.kotcrab.vis.ui.widget.spinner.Spinner;
import com.lomekwi.cave.app.App;
import com.lomekwi.cave.pipeline.Param;
import com.lomekwi.cave.project.Project;
import com.lomekwi.cave.timeline.UndoManager;
import com.lomekwi.cave.timeline.playback.RefreshRequestEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import games.spooky.gdx.nativefilechooser.NativeFileChooserCallback;
import games.spooky.gdx.nativefilechooser.NativeFileChooserConfiguration;
import games.spooky.gdx.nativefilechooser.NativeFileChooserIntent;

/**
 * 把 {@link Param} 条目自动生成为 widget 并绑定到数据模型。
 * 处理变更通知、撤销记录（即时/批量）与模型→widget 的回显同步。
 */
public final class ParamBinder {
    /** 绑定宿主：提供撤销归属与失效回调。 */
    public interface Owner {
        com.lomekwi.cave.pipeline.Source<?> source();

        default void invalidateDetailActor() {}
    }

    private static final long COMMIT_DELAY_NANOS = 400_000_000L;

    private final Owner owner;
    private final List<Runnable> syncers = new ArrayList<>();
    private final Map<Param<?>, Object> batchOld = new LinkedHashMap<>();
    private boolean suppress;
    private boolean pendingBatch;
    private long lastEditTime = -1;

    public ParamBinder(Owner owner) {
        this.owner = owner;
    }

    /** 把参数条目按顺序生成到表格中。 */
    public void bind(VisTable table, List<Param<?>> params) {
        for (Param<?> p : params) {
            if (p instanceof Param.Info info) addInfoRow(table, info);
            else if (p instanceof Param.FloatSpin f) addFloatRow(table, f);
            else if (p instanceof Param.IntSpin i) addIntRow(table, i);
            else if (p instanceof Param.BoolCheck b) addBoolRow(table, b);
            else if (p instanceof Param.TextInput t) addTextRow(table, t);
            else if (p instanceof Param.FileSelect fs) addFileRow(table, fs);
            else if (p instanceof Param.Choice c) addChoiceRow(table, c);
        }
    }

    /** 每帧调用：批量编辑静默期结束后提交撤销命令。 */
    public void tick() {
        if (!pendingBatch || System.nanoTime() - lastEditTime <= COMMIT_DELAY_NANOS) return;
        pendingBatch = false;
        Project p = App.root.getFrontendProject();
        if (p == null) {
            batchOld.clear();
            return;
        }
        List<UndoManager.UndoableCommand> cmds = new ArrayList<>();
        for (Map.Entry<Param<?>, Object> e : batchOld.entrySet()) {
            @SuppressWarnings("unchecked")
            Param<Object> param = (Param<Object>) e.getKey();
            Object cur = param.get();
            if (!Objects.equals(e.getValue(), cur)) {
                cmds.add(new UndoManager.ParamChangeCommand<>(owner, param, e.getValue(), cur));
            }
        }
        batchOld.clear();
        if (cmds.isEmpty()) return;
        UndoManager.UndoableCommand cmd = cmds.size() == 1
            ? cmds.get(0)
            : new UndoManager.CompoundCommand(cmds.toArray(UndoManager.UndoableCommand[]::new));
        p.undoManager.record(cmd);
    }

    /** 模型被外部（gizmo、undo 等）修改后，把当前值回显到 widget。 */
    public void syncFromModel() {
        suppress = true;
        try {
            for (Runnable r : syncers) r.run();
        } finally {
            suppress = false;
        }
    }

    private void applyChange(Param<?> p, Object newValue) {
        if (Objects.equals(newValue, p.get())) return;
        switch (p.undoMode) {
            case IMMEDIATE -> recordCommand(p, p.get(), newValue);
            case BATCHED -> {
                batchOld.putIfAbsent(p, p.get());
                lastEditTime = System.nanoTime();
                pendingBatch = true;
            }
            case NONE -> {}
        }
        setQuietly(p, newValue);
        postRefreshBus();
    }

    private void recordCommand(Param<?> p, Object oldValue, Object newValue) {
        Project proj = App.root.getFrontendProject();
        if (proj == null) return;
        @SuppressWarnings("unchecked")
        Param<Object> typed = (Param<Object>) p;
        proj.undoManager.record(new UndoManager.ParamChangeCommand<>(owner, typed, oldValue, newValue));
    }

    private void setQuietly(Param<?> p, Object value) {
        suppress = true;
        try {
            @SuppressWarnings("unchecked")
            Param<Object> typed = (Param<Object>) p;
            typed.set(value);
        } finally {
            suppress = false;
        }
    }

    private static void postRefreshBus() {
        Project p = App.root.getFrontendProject();
        if (p != null) {
            p.projEventBus.post(RefreshRequestEvent.INSTANCE);
        }
    }

    private void addInfoRow(VisTable table, Param.Info p) {
        table.add(new VisLabel(i18n(p.label))).pad(4).left();
        VisLabel value = new VisLabel("");
        value.setText(p.get());
        table.add(value).pad(4).left().row();
    }

    private void addFloatRow(VisTable table, Param.FloatSpin p) {
        SimpleFloatSpinnerModel model = new SimpleFloatSpinnerModel(
            p.get().floatValue(), (float) p.min, (float) p.max, (float) p.step, p.decimals);
        Spinner spinner = new Spinner("", model);
        spinner.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (suppress) return;
                applyChange(p, (double) ((SimpleFloatSpinnerModel) spinner.getModel()).getValue());
            }
        });
        syncers.add(() -> {
            suppress = true;
            try { model.setValue(p.get().floatValue()); } finally { suppress = false; }
        });
        table.add(new VisLabel(i18n(p.label))).pad(4).left();
        table.add(spinner).width(90).pad(4).row();
    }

    private void addIntRow(VisTable table, Param.IntSpin p) {
        IntSpinnerModel model = new IntSpinnerModel(p.get(), p.min, p.max, p.step);
        Spinner spinner = new Spinner("", model);
        spinner.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (suppress) return;
                applyChange(p, ((IntSpinnerModel) spinner.getModel()).getValue());
            }
        });
        syncers.add(() -> {
            suppress = true;
            try { model.setValue(p.get(), false); } catch (Exception ignored) {}
            finally { suppress = false; }
        });
        table.add(new VisLabel(i18n(p.label))).pad(4).left();
        table.add(spinner).width(90).pad(4).row();
    }

    private void addBoolRow(VisTable table, Param.BoolCheck p) {
        VisCheckBox box = new VisCheckBox("", Boolean.TRUE.equals(p.get()));
        box.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (suppress) return;
                applyChange(p, box.isChecked());
            }
        });
        syncers.add(() -> {
            suppress = true;
            try { box.setChecked(Boolean.TRUE.equals(p.get())); } finally { suppress = false; }
        });
        table.add(new VisLabel(i18n(p.label))).pad(4).left();
        table.add(box).pad(4).row();
    }

    private void addTextRow(VisTable table, Param.TextInput p) {
        VisTextField field;
        if (p.multiline) {
            VisTextArea area = new VisTextArea(p.get());
            area.setPrefRows(3);
            field = area;
            table.add(new VisLabel(i18n(p.label))).pad(4).align(Align.top | Align.left);
            table.add(field).growX().pad(4).row();
        } else {
            field = new VisTextField(p.get());
            table.add(new VisLabel(i18n(p.label))).pad(4).left();
            table.add(field).growX().pad(4).row();
        }
        field.setTextFieldListener((f, c) -> {
            if (suppress) return;
            applyChange(p, f.getText());
        });
        syncers.add(() -> {
            suppress = true;
            try { field.setText(p.get()); } finally { suppress = false; }
        });
    }

    private void addFileRow(VisTable table, Param.FileSelect p) {
        VisValidatableTextField field = new VisValidatableTextField(p.get());
        VisTextButton browseBtn = new VisTextButton(i18n("浏览"));
        table.add(new VisLabel(i18n(p.label))).pad(4).left();
        table.add(field).growX().pad(4);
        table.add(browseBtn).pad(4).row();
        browseBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                NativeFileChooserConfiguration conf = new NativeFileChooserConfiguration();
                conf.title = i18n(p.dialogTitle);
                conf.intent = NativeFileChooserIntent.OPEN;
                conf.nameFilter = (dir, name) -> name != null &&
                    Arrays.stream(p.extensions).anyMatch(name::endsWith);
                App.fileChooser.chooseFile(conf, new NativeFileChooserCallback() {
                    @Override
                    public void onFileChosen(FileHandle file) {
                        String path = file.file().getAbsolutePath();
                        field.setText(path);
                        applyChange(p, path);
                    }

                    @Override
                    public void onCancellation() {}

                    @Override
                    public void onError(Exception exception) {
                        Gdx.app.error("ParamBinder", i18n("选择文件失败"), exception);
                    }
                });
            }
        });
        field.setTextFieldListener((f, c) -> {
            if (suppress) return;
            applyChange(p, f.getText());
        });
        syncers.add(() -> {
            suppress = true;
            try { field.setText(p.get()); } finally { suppress = false; }
        });
    }

    private void addChoiceRow(VisTable table, Param.Choice p) {
        VisSelectBox<String> select = new VisSelectBox<>();
        select.setItems(p.options);
        int cur = Math.max(0, Math.min(p.options.length - 1, p.get()));
        select.setSelectedIndex(cur);
        select.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (suppress) return;
                applyChange(p, select.getSelectedIndex());
            }
        });
        syncers.add(() -> {
            int i = Math.max(0, Math.min(p.options.length - 1, p.get()));
            suppress = true;
            try { select.setSelectedIndex(i); } finally { suppress = false; }
        });
        table.add(new VisLabel(i18n(p.label))).pad(4).left();
        table.add(select).width(110).pad(4).row();
    }
}
