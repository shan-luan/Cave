package com.lomekwi.cave.ui.editpanel.filetree;


import com.badlogic.gdx.Gdx;
import com.kotcrab.vis.ui.widget.VisTree;

import java.io.File;

import static com.lomekwi.cave.util.i18n.I18N.i18n;

public class FileTree extends VisTree<FileTreeNode, File> {
    private static FileTree INSTANCE;

    public FileTree() {
        super();
        INSTANCE = this;

        File rootFile = new File(System.getProperty("user.home"));
        Gdx.app.debug("FileTree", i18n("创建文件树，根目录: ") + rootFile.getAbsolutePath());
        FileTreeNode rootNode = new FileTreeNode(rootFile);

        add(rootNode);
    }

    public static FileTree getINSTANCE() {
        return INSTANCE;
    }
}
