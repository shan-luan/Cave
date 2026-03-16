package com.lomekwi.cave.ui.editpanel.filetree;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.kotcrab.vis.ui.widget.VisTree;

import java.io.File;

import java.io.File;

public class FileTree extends VisTree<FileTreeNode, File> {
    private static FileTree INSTANCE;

    public FileTree() {
        super();
        INSTANCE = this;

        // 获取当前工作目录
        File rootFile = new File(System.getProperty("user.dir"));
        FileTreeNode rootNode = createNodeRecursive(rootFile);

        // 将根节点加入树
        add(rootNode);
    }

    /**
     * 递归创建节点
     */
    private FileTreeNode createNodeRecursive(File file) {
        FileTreeNode node = new FileTreeNode(file);

        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    FileTreeNode childNode = createNodeRecursive(child);
                    node.add(childNode); // 添加子节点
                }
            }
        }

        return node;
    }

    public static FileTree getINSTANCE() {
        return INSTANCE;
    }
}
