package com.lomekwi.cave.ui.editpanel.detail;

import static com.lomekwi.cave.util.i18n.I18N.i18n;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTextButton;
import com.kotcrab.vis.ui.widget.VisTextField;
import com.kotcrab.vis.ui.widget.VisValidatableTextField;
import com.kotcrab.vis.ui.widget.spinner.IntSpinnerModel;
import com.kotcrab.vis.ui.widget.spinner.Spinner;
import com.lomekwi.cave.app.App;
import com.lomekwi.cave.pipeline.text.TextSrc;
import com.lomekwi.cave.timeline.playback.RefreshRequestEvent;

import games.spooky.gdx.nativefilechooser.NativeFileChooserCallback;
import games.spooky.gdx.nativefilechooser.NativeFileChooserConfiguration;
import games.spooky.gdx.nativefilechooser.NativeFileChooserIntent;

public class TextSrcActor extends SourceActor {

    public TextSrcActor(TextSrc src) {
        super(src.getDisplayName());
        VisLabel label = new VisLabel(i18n("文本: "));
        add(label).pad(4).left();
        VisTextField textField = new VisTextField(src.getText());
        add(textField).growX().pad(4).row();
        textField.setTextFieldListener((field, c) -> {
            src.setText(field.getText());
            postRefresh(src);
        });

        add(new VisLabel(i18n("字体: "))).pad(4).left();
        VisValidatableTextField fontPathField = new VisValidatableTextField(src.getFontPath());
        add(fontPathField).growX().pad(4);
        VisTextButton browseBtn = new VisTextButton(i18n("浏览"));
        add(browseBtn).pad(4).row();
        browseBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                NativeFileChooserConfiguration conf = new NativeFileChooserConfiguration();
                conf.title = i18n("选择字体文件");
                conf.intent = NativeFileChooserIntent.OPEN;
                conf.nameFilter = (dir, name) -> name != null &&
                    (name.endsWith(".ttf") || name.endsWith(".otf") || name.endsWith(".ttc"));
                App.fileChooser.chooseFile(conf, new NativeFileChooserCallback() {
                    @Override
                    public void onFileChosen(FileHandle file) {
                        String path = file.file().getAbsolutePath();
                        fontPathField.setText(path);
                        src.setFontPath(path);
                        postRefresh(src);
                    }

                    @Override
                    public void onCancellation() {}

                    @Override
                    public void onError(Exception exception) {
                        Gdx.app.error("TextSrcActor", i18n("选择字体失败"), exception);
                    }
                });
            }
        });
        fontPathField.setTextFieldListener((field, c) -> {
            src.setFontPath(field.getText());
            postRefresh(src);
        });

        add(new VisLabel(i18n("字号: "))).pad(4).left();
        IntSpinnerModel sizeModel = new IntSpinnerModel(src.getFontSize(), 1, 500, 1);
        Spinner sizeSpinner = new Spinner("", sizeModel);
        add(sizeSpinner).width(80).pad(4).row();
        sizeSpinner.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                src.setFontSize(sizeModel.getValue());
                postRefresh(src);
            }
        });
    }

    private static void postRefresh(TextSrc src) {
        var p = App.root.getFrontendProject();
        if (p != null) {
            p.projEventBus.post(RefreshRequestEvent.INSTANCE);
        }
    }
}
