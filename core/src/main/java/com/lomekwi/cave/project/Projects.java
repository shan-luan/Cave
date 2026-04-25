package com.lomekwi.cave.project;

import com.badlogic.gdx.files.FileHandle;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;

public final class Projects {
    public static Project open(FileHandle fileHandle) throws IOException, ClassNotFoundException {
        ObjectInputStream ois = new ObjectInputStream(fileHandle.read());
        Project p = (Project) ois.readObject();
        p.savePath = fileHandle.file().toPath();
        return p;
    }
    public static Project create() {
        return new Project();
    }
    public static void save(Project project, FileHandle fileHandle) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(project);
        }
        byte[] data = baos.toByteArray();
        try (OutputStream os = fileHandle.write(false)) {
            os.write(data);
        }
    }
    public static void save(Project project) throws IOException {
        if(project.savePath == null) {
            throw new FileNotFoundException();
        }
        save(project, new FileHandle(project.savePath.toFile()));
    }
}
