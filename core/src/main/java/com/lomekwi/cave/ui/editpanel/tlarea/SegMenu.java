package com.lomekwi.cave.ui.editpanel.tlarea;

import com.kotcrab.vis.ui.widget.MenuItem;
import com.kotcrab.vis.ui.widget.PopupMenu;
import com.lomekwi.cave.ui.listeners.ChangeListenerX;

public class SegMenu extends PopupMenu {
    private static SegMenu INSTANCE;
    private TlGroup tlGroup;
    private SegActor segActor;
    private SegMenu(){
        addItem(new MenuItem("删除",new ChangeListenerX(()-> {
            tlGroup.removeSeg(segActor);
        })));
    }
    public static SegMenu getInstance(){
        if(INSTANCE==null){
            INSTANCE = new SegMenu();
        }
        return INSTANCE;
    }
    public void setContext(TlGroup tlGroup,SegActor segActor){
        this.tlGroup=tlGroup;
        this.segActor=segActor;
    }
}
