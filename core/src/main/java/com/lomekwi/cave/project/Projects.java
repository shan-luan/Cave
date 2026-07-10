package com.lomekwi.cave.project;

import com.badlogic.gdx.files.FileHandle;
import com.lomekwi.cave.util.FileNameUtil;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;

public final class Projects {
    public static final String PROJECT_EXTENSION = ".cave";

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
        FileHandle target = ensureExtension(fileHandle);
        project.savedVersion = project.currentVersion;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(project);
        }
        byte[] data = baos.toByteArray();
        try (OutputStream os = target.write(false)) {
            os.write(data);
        }
        project.savePath = target.file().toPath();
        project.projEventBus.post(ProjectDirtyChangedEvent.INSTANCE);
    }
    public static void save(Project project) throws IOException {
        if(project.savePath == null) {
            throw new FileNotFoundException();
        }
        save(project, new FileHandle(project.savePath.toFile()));
    }

    public static boolean hasProjectExtension(String name) {
        return name != null && name.toLowerCase().endsWith(PROJECT_EXTENSION);
    }

    public static FileHandle ensureExtension(FileHandle fileHandle) {
        if (hasProjectExtension(fileHandle.name())) return fileHandle;
        return new FileHandle(FileNameUtil.ensureExtension(fileHandle.file(), PROJECT_EXTENSION));
    }
}
