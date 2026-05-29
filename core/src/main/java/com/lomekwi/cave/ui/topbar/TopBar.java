package com.lomekwi.cave.ui.topbar;

import static com.lomekwi.cave.app.Vars.fileChooser;
import static com.lomekwi.cave.util.i18n.I18N.i18n;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.scenes.scene2d.ui.Dialog;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.google.common.eventbus.Subscribe;
import com.kotcrab.vis.ui.util.dialog.Dialogs;
import com.kotcrab.vis.ui.widget.LinkLabel;
import com.kotcrab.vis.ui.widget.Menu;
import com.kotcrab.vis.ui.widget.MenuBar;
import com.kotcrab.vis.ui.widget.MenuItem;
import com.kotcrab.vis.ui.widget.VisDialog;
import com.lomekwi.cave.project.ProjectLoadedEvent;
import com.lomekwi.cave.project.Projects;
import com.lomekwi.cave.ui.Root;
import com.lomekwi.cave.ui.listeners.ChangeListenerX;
import com.lomekwi.cave.ui.tabs.SettingsOpenedEvent;
import com.lomekwi.cave.ui.tabs.TabSwitchedEvent;
import com.lomekwi.cave.app.Vars;

import java.io.IOException;

import games.spooky.gdx.nativefilechooser.NativeFileChooserCallback;
import games.spooky.gdx.nativefilechooser.NativeFileChooserConfiguration;
import games.spooky.gdx.nativefilechooser.NativeFileChooserIntent;

import static com.badlogic.gdx.Input.Keys.*;

public class TopBar extends MenuBar {
    private final float toastTimeOut = 2f;

    public TopBar() {
        super();

        addMenu(new MenuX(i18n("文件"))
            .withItem(new MenuItem(i18n("新建"), new ChangeListenerX(() -> {
                try {
                    Vars.appEventBus.post(new ProjectLoadedEvent(Projects.create()));
                    Root.getInstance().getToastManager().show(i18n("项目已新建"), toastTimeOut);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            })).setShortcut(CONTROL_LEFT, N))
            .withItem(new MenuItem(i18n("打开"), new ChangeListenerX(() -> {
                try {
                    NativeFileChooserConfiguration conf = new NativeFileChooserConfiguration();
                    conf.title = i18n("选择项目...");
                    if (Gdx.app.getType() == Application.ApplicationType.Android) {
                        conf.mimeFilter = "*/*";
                    }
                    conf.intent = NativeFileChooserIntent.OPEN;
                    fileChooser.chooseFile(conf, new NativeFileChooserCallback() {
                        @Override
                        public void onFileChosen(FileHandle file) {
                            try {
                                Vars.appEventBus.post(new ProjectLoadedEvent(Projects.open(file)));
                                Root.getInstance().getToastManager().show(i18n("项目已打开"), toastTimeOut);
                            } catch (IOException | ClassNotFoundException e) {
                                e.printStackTrace();
                            }
                        }
                        @Override
                        public void onCancellation() {}
                        @Override
                        public void onError(Exception exception) {}
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                }
            })).setShortcut(CONTROL_LEFT, O))
            .withSeparator()
            .withItem(new MenuItemP(i18n("保存"), new ChangeListenerX(() -> {
                try {
                    if (Root.getInstance().getFrontendProject() == null) {
                        return;
                    }

                    if (Root.getInstance().getFrontendProject().getSavePath() == null) {
                        NativeFileChooserConfiguration conf = new NativeFileChooserConfiguration();
                        conf.title = i18n("选择保存位置...");
                        conf.mimeFilter = "*/*";
                        conf.intent = NativeFileChooserIntent.SAVE;
                        fileChooser.chooseFile(conf, new NativeFileChooserCallback() {
                            @Override
                            public void onFileChosen(FileHandle file) {
                                try {
                                    if (Root.getInstance().getFrontendProject() != null) {
                                        Projects.save(Root.getInstance().getFrontendProject(), file);
                                        Root.getInstance().getToastManager().show(i18n("项目已保存"), toastTimeOut);
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
                    } else {
                        Projects.save(Root.getInstance().getFrontendProject());
                        Root.getInstance().getToastManager().show(i18n("项目已保存"), toastTimeOut);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            })).setShortcut(CONTROL_LEFT, S))
            .withItem(new MenuItemP(i18n("另存为"), new ChangeListenerX(() -> {
                try {
                    if (Root.getInstance().getFrontendProject() == null) {
                        return;
                    }

                    NativeFileChooserConfiguration conf = new NativeFileChooserConfiguration();
                    conf.title = i18n("选择保存位置...");
                    conf.mimeFilter = "*/*";
                    conf.intent = NativeFileChooserIntent.SAVE;
                    fileChooser.chooseFile(conf, new NativeFileChooserCallback() {
                        @Override
                        public void onFileChosen(FileHandle file) {
                            try {
                                if (Root.getInstance().getFrontendProject() != null) {
                                    Projects.save(Root.getInstance().getFrontendProject(), file);
                                    Root.getInstance().getToastManager().show(i18n("项目已保存"), toastTimeOut);
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
                } catch (Exception e) {
                    e.printStackTrace();
                }
            })).setShortcut(CONTROL_LEFT, SHIFT_LEFT, S))
            .withSeparator()
            .withItem(new MenuItem(i18n("关闭"), new ChangeListenerX(() -> {
                try {
                    Gdx.app.exit();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            })).setShortcut(CONTROL_LEFT, W)));

        addMenu(new MenuX(i18n("工具"))
            .withItem(new MenuItem(i18n("设置"), new ChangeListenerX(() -> {
                Vars.appEventBus.post(SettingsOpenedEvent.INSTANCE);
            })))
        );

        addMenu(new MenuX(i18n("帮助"))
            .withItem(new MenuItem(i18n("关于"), new ChangeListenerX(() -> {
                VisDialog about = new VisDialog(i18n("关于"));
                var ct = about.getContentTable();
                ct.add(new Label(i18n("CAVE:Cave is Another Video Editor是自由的多媒体编辑软件"), about.getSkin())).left();
                ct.row();
                ct.add(new Label(i18n("由shan_luan_开发.此软件以AGPLv3分发并不提供任何保修."), about.getSkin())).left();
                ct.row();
                ct.add(new LinkLabel("Github","https://github.com/shan-luan/Cave")).left();
                ct.row();
                ct.add(new LinkLabel(i18n("B站"),"https://space.bilibili.com/1655518235")).left();
                ct.row();
                about.button(i18n("确定"), true);
                about.show(Root.getInstance().getStage());
            }))
        ));
    }

    public static class MenuX extends Menu {
        public MenuX(String title) {
            super(title);
        }
        public MenuX withItem(MenuItem item) {
            super.addItem(item);
            return this;
        }
        public MenuX withSeparator() {
            super.addSeparator();
            return this;
        }
    }

    /**
     * 与项目关联的菜单项，当当前没有可用项目时会自动禁用
     */
    public static class MenuItemP extends MenuItem {
        public MenuItemP(String text) {
            super(text);
            Vars.appEventBus.register(this);
            setDisabled(true);
        }
        public MenuItemP(String text, ChangeListener changeListener) {
            super(text, changeListener);
            Vars.appEventBus.register(this);
            setDisabled(true);
        }
        @Subscribe
        public void onTabSwitched(TabSwitchedEvent event) {
            setDisabled(Root.getInstance().getFrontendProject() == null);
        }
    }
}
