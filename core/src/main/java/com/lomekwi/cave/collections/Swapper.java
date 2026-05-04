package com.lomekwi.cave.collections;

public class Swapper<T> {
    public T a;
    public T b;
    public boolean aFronted;
    public Swapper(T a, T b) {
        this.a = a;
        this.b = b;
        aFronted = true;
    }
    public void setFront(T newValue) {
        if(aFronted){
            a = newValue;
        }else{
            b = newValue;
        }
    }
    public void setBack(T newValue) {
        if(aFronted){
            b = newValue;
        }else{
            a = newValue;
        }
    }
    public T getFront() {
        return aFronted ? a : b;
    }
    public T getBack() {
        return aFronted ? b : a;
    }
    public void swap() {
        aFronted = !aFronted;
    }
}
