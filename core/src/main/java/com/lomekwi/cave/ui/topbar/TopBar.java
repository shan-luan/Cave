package com.lomekwi.cave.ui.topbar;

import static com.lomekwi.cave.app.App.fileChooser;
import static com.lomekwi.cave.util.Units.MEGA;
import static com.lomekwi.cave.util.i18n.I18N.i18n;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.google.common.eventbus.Subscribe;
import com.kotcrab.vis.ui.widget.LinkLabel;
import com.kotcrab.vis.ui.widget.Menu;
import com.kotcrab.vis.ui.widget.MenuBar;
import com.kotcrab.vis.ui.widget.MenuItem;
import com.kotcrab.vis.ui.widget.VisDialog;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisProgressBar;
import com.kotcrab.vis.ui.widget.VisTable;
import com.lomekwi.cave.project.ProjectLoadedEvent;
import com.lomekwi.cave.project.Projects;
import com.lomekwi.cave.task.Task;
import com.lomekwi.cave.task.VideoExportTask;
import com.lomekwi.cave.ui.Root;
import com.lomekwi.cave.ui.listeners.ChangeListenerX;
import com.lomekwi.cave.ui.tabs.SettingsOpenedEvent;
import com.lomekwi.cave.ui.tabs.TabSwitchedEvent;
import com.lomekwi.cave.app.App;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;

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
                    App.appEventBus.post(new ProjectLoadedEvent(Projects.create()));
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
                                App.appEventBus.post(new ProjectLoadedEvent(Projects.open(file)));
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
            .withItem(new MenuItemP(i18n("导出"), new ChangeListenerX(() -> {

                var project = Root.getInstance().getFrontendProject();
                if (project == null) return;
                var outputFile = new File(System.getProperty("java.io.tmpdir"), "cave_export_test.mp4");
                var task = new VideoExportTask(
                    project.timeline.duplicate(),
                    outputFile,
                    1920, 1080, 30.0,
                    0f, 0f, (int) (6*MEGA)
                );
                App.taskPool.submit(task);
            })))
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
                App.appEventBus.post(SettingsOpenedEvent.INSTANCE);
            })))
            .withItem(new MenuItem(i18n("后台任务"), new ChangeListenerX(() -> {

                VisDialog taskWin = new VisDialog(i18n("后台任务")) {
                    final HashMap<Task, VisTable> rows = new HashMap<>();
                    final HashSet<Task> current = new HashSet<>();
                    boolean dirty;

                    @Override
                    public void act(float delta) {
                        super.act(delta);
                        Table content = getContentTable();

                        current.clear();
                        for (Task task : App.taskPool) {
                            current.add(task);
                            VisTable row = rows.get(task);
                            if (row == null) {
                                dirty = true;
                                row = new VisTable();
                                VisProgressBar bar = new VisProgressBar(0, 1, 0.01f, false);
                                row.add(new VisLabel(task.getName())).left();
                                row.add(bar).growX();
                                row.setUserObject(bar);
                                rows.put(task, row);
                                content.add(row).growX();
                                content.row();
                            }
                            ((VisProgressBar) row.getUserObject()).setValue(task.getProgress());
                        }

                        rows.entrySet().removeIf(entry -> {
                            if (!current.contains(entry.getKey())) {
                                dirty = true;
                                content.removeActor(entry.getValue());
                                return true;
                            }
                            return false;
                        });
                        if (dirty) {
                            content.pack();
                            pack();
                            dirty = false;
                        }
                    }
                };
                taskWin.addCloseButton();
                taskWin.show(Root.getInstance().getStage());
            })))
        );

        addMenu(new MenuX(i18n("帮助"))
            .withItem(new MenuItem(i18n("关于"), new ChangeListenerX(() -> {
                VisDialog about = new VisDialog(i18n("关于"));
                about.addCloseButton();
                var ct = about.getContentTable();
                ct.add(new Label(i18n("CAVE:Cave is Another Video Editor是自由的多媒体编辑软件"), about.getSkin())).left();
                ct.row();
                ct.add(new Label(i18n("由shan_luan_开发.此软件以AGPLv3分发并不提供任何保修."), about.getSkin())).left();
                ct.row();
                ct.add(new LinkLabel("Github","https://github.com/shan-luan/Cave")).left();
                ct.row();
                ct.add(new LinkLabel(i18n("B站"),"https://space.bilibili.com/1655518235")).left();
                ct.row();
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
            App.appEventBus.register(this);
            setDisabled(true);
        }
        public MenuItemP(String text, ChangeListener changeListener) {
            super(text, changeListener);
            App.appEventBus.register(this);
            setDisabled(true);
        }
        @Subscribe
        public void onTabSwitched(TabSwitchedEvent event) {
            setDisabled(Root.getInstance().getFrontendProject() == null);
        }
    }
}
