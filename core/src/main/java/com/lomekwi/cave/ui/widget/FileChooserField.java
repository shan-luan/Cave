package com.lomekwi.cave.ui.widget;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.Gdx;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisTextButton;
import com.kotcrab.vis.ui.widget.VisValidatableTextField;
import com.lomekwi.cave.app.App;

import games.spooky.gdx.nativefilechooser.NativeFileChooserCallback;
import games.spooky.gdx.nativefilechooser.NativeFileChooserConfiguration;
import games.spooky.gdx.nativefilechooser.NativeFileChooserIntent;

/**
 * 一个带文本字段和浏览按钮的文件选择器小组件。
 * 点击浏览按钮会弹出系统原生文件选择对话框，选中后路径自动填入文本框。
 */
public class FileChooserField extends VisTable {
    private final VisValidatableTextField pathField;
    private final VisTextButton browseBtn;
    private final String chooserTitle;
    private final NativeFileChooserIntent intent;

    public FileChooserField(String chooserTitle, NativeFileChooserIntent intent) {
        this.chooserTitle = chooserTitle;
        this.intent = intent;

        pathField = new VisValidatableTextField("");
        browseBtn = new VisTextButton("浏览");

        browseBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                openChooser();
            }
        });

        add(pathField).growX().padRight(8);
        add(browseBtn);
    }

    private void openChooser() {
        NativeFileChooserConfiguration conf = new NativeFileChooserConfiguration();
        conf.title = chooserTitle;
        conf.intent = intent;
        conf.nameFilter = (dir, name) -> true;
        App.fileChooser.chooseFile(conf, new NativeFileChooserCallback() {
            @Override
            public void onFileChosen(FileHandle file) {
                pathField.setText(file.file().getAbsolutePath());
            }

            @Override
            public void onCancellation() {}

            @Override
            public void onError(Exception exception) {
                Gdx.app.error("FileChooserField", "选择文件失败", exception);
            }
        });
    }

    /** 获取当前路径文本（已 trim）。 */
    public String getPath() {
        return pathField.getText().trim();
    }

    /** 设置路径文本。 */
    public void setPath(String path) {
        pathField.setText(path);
    }

    /** 暴露底层文本框，以便进行更细粒度的控制（如添加验证器）。 */
    public VisValidatableTextField getPathField() {
        return pathField;
    }
}
