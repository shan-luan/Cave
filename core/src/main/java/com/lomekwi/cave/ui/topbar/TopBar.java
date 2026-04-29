package com.lomekwi.cave.ui.topbar;

import static com.lomekwi.cave.util.Vars.fileChooser;
import static com.lomekwi.cave.util.i18n.I18N.i18n;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.google.common.eventbus.Subscribe;
import com.kotcrab.vis.ui.widget.Menu;
import com.kotcrab.vis.ui.widget.MenuBar;
import com.kotcrab.vis.ui.widget.MenuItem;
import com.kotcrab.vis.ui.widget.toast.Toast;
import com.lomekwi.cave.project.ProjectEvents;
import com.lomekwi.cave.project.Projects;
import com.lomekwi.cave.ui.Root;
import com.lomekwi.cave.ui.tabs.TabEvents;
import com.lomekwi.cave.util.Vars;

import java.io.IOException;

import games.spooky.gdx.nativefilechooser.NativeFileChooserCallback;
import games.spooky.gdx.nativefilechooser.NativeFileChooserConfiguration;
import games.spooky.gdx.nativefilechooser.NativeFileChooserIntent;

import static com.badlogic.gdx.Input.Keys.*;

public class TopBar extends MenuBar {
    private final float toastTimeOut = 2f;
    public TopBar() {
        super();
//TODO:实现快捷键
        addMenu(new MenuX(i18n("文件"))
            .withItem(new MenuItem(i18n("新建"),new ChangeListenerX(() -> {
                Vars.appEventBus.post(new ProjectEvents.ProjectLoadedEvent(Projects.create()));
                Root.getInstance().getToastManager().show(i18n("项目已新建"),toastTimeOut);
            }
            )).setShortcut(CONTROL_LEFT,N))
            .withItem(new MenuItem(i18n("打开"),new ChangeListenerX(() -> {
                NativeFileChooserConfiguration conf = new NativeFileChooserConfiguration();
                conf.title = i18n("选择项目...");
                if(Gdx.app.getType()== Application.ApplicationType.Android) {
                    conf.mimeFilter = "*/*";
                }
                conf.intent = NativeFileChooserIntent.OPEN;
                fileChooser.chooseFile(conf, new NativeFileChooserCallback() {
                    @Override
                    public void onFileChosen(FileHandle file) {
                        try {
                            Vars.appEventBus.post(new ProjectEvents.ProjectLoadedEvent(Projects.open(file)));
                            Root.getInstance().getToastManager().show(i18n("项目已打开"),toastTimeOut);
                        } catch (IOException | ClassNotFoundException e) {
                            e.printStackTrace();
                        }
                    }
                    @Override
                    public void onCancellation() {}
                    @Override
                    public void onError(Exception exception) {}
                });
                })
            ).setShortcut(CONTROL_LEFT,O))
            .withItem(new MenuItemP(i18n("保存"),new ChangeListenerX(()->{
                if(Root.getInstance().getFrontendProject().getSavePath()==null) {
                    NativeFileChooserConfiguration conf = new NativeFileChooserConfiguration();
                    conf.title = i18n("选择保存位置...");
                    conf.mimeFilter = "*/*";
                    conf.intent = NativeFileChooserIntent.SAVE;
                    fileChooser.chooseFile(conf, new NativeFileChooserCallback() {
                        @Override
                        public void onFileChosen(FileHandle file) {
                            try {
                                if(Root.getInstance().getFrontendProject()!=null) {
                                    Projects.save(Root.getInstance().getFrontendProject(), file);
                                    Root.getInstance().getToastManager().show(i18n("项目已保存"),toastTimeOut);
                                }
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                        @Override
                        public void onCancellation() {}
                        @Override
                        public void onError(Exception exception) {}
                    });
                }else {
                    try {
                        Projects.save(Root.getInstance().getFrontendProject());
                        Root.getInstance().getToastManager().show(i18n("项目已保存"),toastTimeOut);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            })).setShortcut(CONTROL_LEFT,S))
            .withItem(new MenuItemP(i18n("另存为"),new ChangeListenerX(()->{
                NativeFileChooserConfiguration conf = new NativeFileChooserConfiguration();
                conf.title = i18n("选择保存位置...");
                conf.mimeFilter = "*/*";
                conf.intent = NativeFileChooserIntent.SAVE;
                fileChooser.chooseFile(conf, new NativeFileChooserCallback() {
                    @Override
                    public void onFileChosen(FileHandle file) {
                        try {
                            if(Root.getInstance().getFrontendProject()!=null) {
                                Projects.save(Root.getInstance().getFrontendProject(), file);
                                Root.getInstance().getToastManager().show(i18n("项目已保存"),toastTimeOut);
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    @Override
                    public void onCancellation() {}
                    @Override
                    public void onError(Exception exception) {}
                });
            })).setShortcut(CONTROL_LEFT,SHIFT_LEFT,S))
            .withItem(new MenuItem(i18n("关闭"),new ChangeListenerX(()->Gdx.app.exit())).setShortcut(CONTROL_LEFT,W)));
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
    /**
     * 与项目关联的菜单项，当当前没有可用项目时会自动禁用
     */
    public static class MenuItemP extends MenuItem{
        public MenuItemP(String text) {
            super(text);
            Vars.appEventBus.register(this);
            setDisabled(true);
        }
        public MenuItemP(String text,ChangeListener changeListener){
            super(text,changeListener);
            Vars.appEventBus.register(this);
            setDisabled(true);
        }
        @Subscribe
        public void onTabSwitched(TabEvents.TabSwitchedEvent event){
            setDisabled(Root.getInstance().getFrontendProject()==null);
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
