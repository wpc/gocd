package com.thoughtworks.go.remote.work;

public interface Callback<T> {
    void call(T result);
}
