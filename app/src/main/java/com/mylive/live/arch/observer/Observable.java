package com.mylive.live.arch.observer;

/**
 * Created by Developer Zailong Shi on 2019-06-20.
 */
public interface Observable<T> {
    void observe(Observer<T> observer);
}
