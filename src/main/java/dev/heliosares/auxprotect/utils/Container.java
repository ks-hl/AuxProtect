package dev.heliosares.auxprotect.utils;

public class Container<T> {
    private T value;

    public Container(T value) {
        this.value = value;
    }

    public Container() {
    }

    public T get() {
        return value;
    }

    public void set(T value) {
        this.value = value;
    }
}
