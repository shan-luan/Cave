package com.lomekwi.cave.ui.topbar;

import com.kotcrab.vis.ui.widget.Menu;
import com.kotcrab.vis.ui.widget.MenuBar;
import com.kotcrab.vis.ui.widget.MenuItem;

public class TopBar extends MenuBar {
    public TopBar() {
        super();
        Menu other = new Menu("Other");
        MenuItem about = new MenuItem("About",new About());
        other.addItem(about);
        addMenu(other);
    }
}
