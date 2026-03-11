package com.lomekwi.cave.ui.topbar;

import static com.lomekwi.cave.util.i18n.I18N.i18n;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.kotcrab.vis.ui.widget.Menu;
import com.kotcrab.vis.ui.widget.MenuBar;
import com.kotcrab.vis.ui.widget.MenuItem;

public class TopBar extends MenuBar {
    public TopBar() {
        super();

        addMenu(new MenuX(i18n("文件"))
            .joinItem(new MenuItem(i18n("新建")))
            .joinItem(new MenuItem(i18n("打开")))
            .joinItem(new MenuItem(i18n("保存")))
            .joinItem(new MenuItem(i18n("另存为")))
            .joinItem(new MenuItem(i18n("关闭"),new ChangeListenerX(()->Gdx.app.exit()))));
    }
    public static class MenuX extends Menu{
        public MenuX(String title) {
            super(title);
        }
        public MenuX joinItem(MenuItem item){
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
