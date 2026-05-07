package com.lomekwi.cave.collections;

public class Swapper<T> {
    public T front;
    public T back;

    public Swapper(T front, T back) {
        this.front = front;
        this.back = back;
    }

    public void swap() {
        T tmp = front;
        front = back;
        back = tmp;
    }
}
