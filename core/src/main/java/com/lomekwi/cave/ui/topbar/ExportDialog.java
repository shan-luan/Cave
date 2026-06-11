package com.lomekwi.cave.ui.topbar;

import static com.lomekwi.cave.util.Units.MEGA;
import static com.lomekwi.cave.util.i18n.I18N.i18n;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.kotcrab.vis.ui.util.Validators;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisScrollPane;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisTextButton;
import com.kotcrab.vis.ui.widget.VisValidatableTextField;
import com.kotcrab.vis.ui.widget.VisDialog;
import com.lomekwi.cave.project.Project;
import com.lomekwi.cave.resource.media.VdoRes;
import com.lomekwi.cave.task.VideoExportTask;
import com.lomekwi.cave.ui.widget.FileChooserField;
import com.lomekwi.cave.app.App;

import games.spooky.gdx.nativefilechooser.NativeFileChooserIntent;

/**
 * 导出视频对话框，提供格式化的参数表单。
 */
public class ExportDialog extends VisDialog {
    private final Project project;
    private final FileChooserField fileChooserField;
    private final VisValidatableTextField widthField;
    private final VisValidatableTextField heightField;
    private final VisValidatableTextField fpsField;
    private final VisValidatableTextField bitrateField;

    public ExportDialog(Project project) {
        super(i18n("导出视频"));
        this.project = project;

        Table content = getContentTable();

        // ---- 可滚动的表单区域 ----
        VisTable form = new VisTable();

        fileChooserField = new FileChooserField(
            i18n("选择导出位置..."),
            NativeFileChooserIntent.SAVE
        );

        VisTable pathRow = new VisTable();
        pathRow.add(new VisLabel(i18n("输出文件"))).left().padRight(8);
        pathRow.add(fileChooserField).growX();
        form.add(pathRow).growX().pad(4).row();

        // ---- 分辨率 ----
        int[] dims = detectDimensions();
        widthField = new VisValidatableTextField(String.valueOf(dims[0]));
        widthField.addValidator(Validators.INTEGERS);
        widthField.addValidator(new Validators.GreaterThanValidator(0));

        heightField = new VisValidatableTextField(String.valueOf(dims[1]));
        heightField.addValidator(Validators.INTEGERS);
        heightField.addValidator(new Validators.GreaterThanValidator(0));

        VisTable dimRow = new VisTable();
        dimRow.add(new VisLabel(i18n("宽度"))).left().padRight(8);
        dimRow.add(widthField).width(80).padRight(16);
        dimRow.add(new VisLabel(i18n("高度"))).left().padRight(8);
        dimRow.add(heightField).width(80);
        form.add(dimRow).growX().pad(4).row();

        // ---- 帧率 ----
        fpsField = new VisValidatableTextField("30.0");
        fpsField.addValidator(Validators.FLOATS);
        fpsField.addValidator(new Validators.GreaterThanValidator(0));

        VisTable fpsRow = new VisTable();
        fpsRow.add(new VisLabel(i18n("帧率"))).left().padRight(8);
        fpsRow.add(fpsField).width(80);
        fpsRow.add(new VisLabel("fps")).padLeft(4);
        form.add(fpsRow).growX().pad(4).row();

        // ---- 码率 ----
        bitrateField = new VisValidatableTextField("6");
        bitrateField.addValidator(Validators.FLOATS);
        bitrateField.addValidator(new Validators.GreaterThanValidator(0));

        VisTable bitrateRow = new VisTable();
        bitrateRow.add(new VisLabel(i18n("码率"))).left().padRight(8);
        bitrateRow.add(bitrateField).width(80);
        bitrateRow.add(new VisLabel("Mbps")).padLeft(4);
        form.add(bitrateRow).growX().pad(4).row();

        VisScrollPane scrollPane = new VisScrollPane(form);
        scrollPane.setFadeScrollBars(false);
        content.add(scrollPane).size(500, 280).row();

        // ---- 按钮 ----
        addCloseButton();

        VisTextButton exportBtn = new VisTextButton(i18n("导出"));
        exportBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                startExport();
            }
        });

        VisTable btnRow = new VisTable();
        btnRow.add(exportBtn);
        content.add(btnRow).padTop(12).row();
    }

    /**
     * 从项目中自动检测首个视频资源的尺寸，无视频时回退 1920×1080。
     */
    private int[] detectDimensions() {
        for (var res : project.resources.values()) {
            if (res instanceof VdoRes v) {
                return new int[]{v.getWidth(), v.getHeight()};
            }
        }
        return new int[]{1920, 1080};
    }

    private void startExport() {
        String path = fileChooserField.getPath();
        if (path.isEmpty()) {
            App.root.getToastManager().show(i18n("请选择输出文件"), 2f);
            return;
        }

        if (!widthField.isInputValid() || !heightField.isInputValid()
            || !fpsField.isInputValid() || !bitrateField.isInputValid()) {
            return;
        }

        int width = Integer.parseInt(widthField.getText().trim());
        int height = Integer.parseInt(heightField.getText().trim());
        double fps = Double.parseDouble(fpsField.getText().trim());
        int bitrate = (int) (Double.parseDouble(bitrateField.getText().trim()) * MEGA);

        var task = new VideoExportTask(
            project.timeline.duplicate(),
            new java.io.File(path),
            width, height, fps,
            0f, 0f, bitrate
        );
        App.taskPool.submit(task);
        App.root.getToastManager().show(i18n("开始导出：") + new java.io.File(path).getName(), 2f);
        fadeOut();
    }
}
