package com.lomekwi.cave.ui.topbar;

import static com.lomekwi.cave.util.Vars.fileChooser;
import static com.lomekwi.cave.util.i18n.I18N.i18n;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.google.common.eventbus.Subscribe;
import com.kotcrab.vis.ui.widget.Menu;
import com.kotcrab.vis.ui.widget.MenuBar;
import com.kotcrab.vis.ui.widget.MenuItem;
import com.lomekwi.cave.project.ProjectEvents;
import com.lomekwi.cave.project.Projects;
import com.lomekwi.cave.ui.Root;
import com.lomekwi.cave.ui.listeners.ChangeListenerX;
import com.lomekwi.cave.ui.tabs.TabEvents;
import com.lomekwi.cave.util.Vars;

import java.io.IOException;

import games.spooky.gdx.nativefilechooser.NativeFileChooserCallback;
import games.spooky.gdx.nativefilechooser.NativeFileChooserConfiguration;
import games.spooky.gdx.nativefilechooser.NativeFileChooserIntent;

import static com.badlogic.gdx.Input.Keys.*;

import com.lomekwi.cave.app.HotkeyManager;

public class TopBar extends MenuBar {
    private final float toastTimeOut = 2f;

    public TopBar() {
        super();

        // 注册热键并创建菜单项
        HotkeyManager hotkeyManager = HotkeyManager.getInstance();

        // 新建项目：Ctrl+N
        hotkeyManager.registerHotkey(
            new HotkeyManager.KeyCombo(true, false, N),
            this::newProject,
            i18n("新建")
        );

        // 打开项目：Ctrl+O
        hotkeyManager.registerHotkey(
            new HotkeyManager.KeyCombo(true, false, O),
            this::openProject,
            i18n("打开")
        );

        // 保存项目：Ctrl+S
        hotkeyManager.registerHotkey(
            new HotkeyManager.KeyCombo(true, false, S),
            this::saveProject,
            i18n("保存")
        );

        // 另存为：Ctrl+Shift+S
        hotkeyManager.registerHotkey(
            new HotkeyManager.KeyCombo(true, true, S),
            this::saveAsProject,
            i18n("另存为")
        );

        // 关闭：Ctrl+W
        hotkeyManager.registerHotkey(
            new HotkeyManager.KeyCombo(true, false, W),
            this::closeApplication,
            i18n("关闭")
        );

        addMenu(new MenuX(i18n("文件"))
            .withItem(new MenuItem(i18n("新建"), new ChangeListenerX(this::newProject))
                .setShortcut(CONTROL_LEFT, N))
            .withItem(new MenuItem(i18n("打开"), new ChangeListenerX(this::openProject))
                .setShortcut(CONTROL_LEFT, O))
            .withItem(new MenuItemP(i18n("保存"), new ChangeListenerX(this::saveProject))
                .setShortcut(CONTROL_LEFT, S))
            .withItem(new MenuItemP(i18n("另存为"), new ChangeListenerX(this::saveAsProject))
                .setShortcut(CONTROL_LEFT, SHIFT_LEFT, S))
            .withItem(new MenuItem(i18n("关闭"), new ChangeListenerX(this::closeApplication))
                .setShortcut(CONTROL_LEFT, W)));

        addMenu(new MenuX(i18n("工具"))
            .withItem(new MenuItem(i18n("设置"), new ChangeListenerX(()->{
                Vars.appEventBus.post(TabEvents.SettingsOpenedEvent.INSTANCE);
            })))
        );
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
     * 新建项目
     */
    private Boolean newProject() {
        try {
            Vars.appEventBus.post(new ProjectEvents.ProjectLoadedEvent(Projects.create()));
            Root.getInstance().getToastManager().show(i18n("项目已新建"), toastTimeOut);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return true;
    }

    /**
     * 打开项目
     */
    private Boolean openProject() {
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
                        Vars.appEventBus.post(new ProjectEvents.ProjectLoadedEvent(Projects.open(file)));
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
        return true;
    }

    /**
     * 保存项目
     */
    private Boolean saveProject() {
        try {
            if (Root.getInstance().getFrontendProject() == null) {
                return true;
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
        return true;
    }

    /**
     * 另存为项目
     */
    private Boolean saveAsProject() {
        try {
            if (Root.getInstance().getFrontendProject() == null) {
                return true;
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
        return true;
    }

    /**
     * 关闭应用
     */
    private Boolean closeApplication() {
        try {
            Gdx.app.exit();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return true;
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
        public MenuItemP(String text, ChangeListener changeListener){
            super(text,changeListener);
            Vars.appEventBus.register(this);
            setDisabled(true);
        }
        @Subscribe
        public void onTabSwitched(TabEvents.TabSwitchedEvent event){
            setDisabled(Root.getInstance().getFrontendProject()==null);
        }
    }
}
