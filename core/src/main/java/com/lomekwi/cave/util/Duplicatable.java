package com.lomekwi.cave.util;

import java.io.*;

public interface Duplicatable<T extends Duplicatable<T>> extends Serializable {

    @SuppressWarnings("unchecked")
    /*
     * 创建一个深拷贝
     */
    default T duplicate() {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(this);
            try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()))) {
                return (T) ois.readObject();
            }
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException("Duplicate failed", e);
        }
    }
}
