package com.lomekwi.cave.project;

import com.badlogic.gdx.files.FileHandle;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;

public final class Projects {
    public static Project open(FileHandle fileHandle) throws IOException, ClassNotFoundException {
        ObjectInputStream ois = new ObjectInputStream(fileHandle.read());
        return (Project) ois.readObject();
    }
    public static Project create() {
        return new Project();
    }
    public static void save(Project project, FileHandle fileHandle) throws IOException {
        try (ObjectOutputStream oos = new ObjectOutputStream(fileHandle.write(false))) {
            oos.writeObject(project);
        }
    }
}
