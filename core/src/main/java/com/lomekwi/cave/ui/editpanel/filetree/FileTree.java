package com.lomekwi.cave.ui.editpanel.filetree;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.kotcrab.vis.ui.widget.VisTree;

import java.io.File;

public class FileTree extends VisTree<FileTreeNode, FileHandle> {
    private static FileTree INSTANCE;

    public FileTree() {
        super();
        INSTANCE = this;

        // 获取当前工作目录
        FileHandle rootHandle = Gdx.files.absolute(System.getProperty("user.dir"));
        FileTreeNode rootNode = createNodeRecursive(rootHandle);

        // 将根节点加入树
        add(rootNode);
    }

    /**
     * 递归创建节点
     */
    private FileTreeNode createNodeRecursive(FileHandle handle) {
        FileTreeNode node = new FileTreeNode(handle);

        if (handle.isDirectory()) {
            for (FileHandle child : handle.list()) {
                FileTreeNode childNode = createNodeRecursive(child);
                node.add(childNode); // 添加子节点
            }
        }

        return node;
    }

    public static FileTree getINSTANCE() {
        return INSTANCE;
    }
}
