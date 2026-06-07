package com.lomekwi.cave.ui.editpanel.tlarea;

import com.kotcrab.vis.ui.widget.MenuItem;
import com.kotcrab.vis.ui.widget.PopupMenu;
import com.lomekwi.cave.ui.listeners.ChangeListenerX;

public class SegMenu extends PopupMenu {
    private static SegMenu INSTANCE;
    private TlGroup tlGroup;
    private SegActor segActor;
    private long time;
    private SegMenu(){
        addItem(new MenuItem("删除",new ChangeListenerX(()-> tlGroup.removeSeg(segActor))));
        addItem(new MenuItem("分割",new ChangeListenerX(()-> tlGroup.split(segActor,time))));
    }
    public static SegMenu getInstance(){
        if(INSTANCE==null){
            INSTANCE = new SegMenu();
        }
        return INSTANCE;
    }
    public void setContext(TlGroup tlGroup,SegActor segActor,long time){
        this.tlGroup=tlGroup;
        this.segActor=segActor;
        this.time=time;
    }
}
