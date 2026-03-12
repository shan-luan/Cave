package com.lomekwi.cave.ui.topbar;

import static com.lomekwi.cave.util.Vars.fileChooser;
import static com.lomekwi.cave.util.i18n.I18N.i18n;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.kotcrab.vis.ui.widget.Menu;
import com.kotcrab.vis.ui.widget.MenuBar;
import com.kotcrab.vis.ui.widget.MenuItem;
import com.lomekwi.cave.project.ProjectEvents;
import com.lomekwi.cave.project.Projects;
import com.lomekwi.cave.util.Vars;

import java.io.IOException;

import games.spooky.gdx.nativefilechooser.NativeFileChooserCallback;
import games.spooky.gdx.nativefilechooser.NativeFileChooserConfiguration;

public class TopBar extends MenuBar {
    public TopBar() {
        super();

        addMenu(new MenuX(i18n("文件"))
            .withItem(new MenuItem(i18n("新建"),new ChangeListenerX(() -> {
                Vars.appEventBus.post(new ProjectEvents.ProjectLoadedEvent(Projects.create()));
            })))
            .withItem(new MenuItem(i18n("打开"),new ChangeListenerX(() -> {
                NativeFileChooserConfiguration conf = new NativeFileChooserConfiguration();
                conf.title = i18n("选择项目...");
                fileChooser.chooseFile(conf, new NativeFileChooserCallback() {
                    @Override
                    public void onFileChosen(FileHandle file) {
                        try {
                            Vars.appEventBus.post(new ProjectEvents.ProjectLoadedEvent(Projects.open(file)));
                        } catch (IOException | ClassNotFoundException e) {
                            e.printStackTrace();
                        }
                    }
                    @Override
                    public void onCancellation() {
                        // 用户取消了选择
                        Gdx.app.log("FileChooser", "取消了");
                    }

                    @Override
                    public void onError(Exception exception) {
                        // 出错了
                        Gdx.app.error("FileChooser", "错误", exception);
                    }
                });
                })
            ))
            .withItem(new MenuItem(i18n("保存")))
            .withItem(new MenuItem(i18n("另存为")))
            .withItem(new MenuItem(i18n("关闭"),new ChangeListenerX(()->Gdx.app.exit()))));
    }
    public static class MenuX extends Menu{
        public MenuX(String title) {
            super(title);
        }
        public MenuX withItem(MenuItem item){
            super.addItem(item);
            return this;
        }
    }
    public static class ChangeListenerX extends ChangeListener {
        private final Runnable runnable;
        public ChangeListenerX(Runnable runnable){
            this.runnable=runnable;
        }
        @Override
        public void changed(ChangeEvent event, Actor actor) {
            runnable.run();
        }
    }
}
