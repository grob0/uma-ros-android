package com.example.umarosandroid;

import androidx.annotation.NonNull;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;

public class ROSLifecycle implements LifecycleOwner {
    private LifecycleRegistry mLifecycleRegistry;
    ROSLifecycle() {
        mLifecycleRegistry = new LifecycleRegistry(this);
        mLifecycleRegistry.setCurrentState(Lifecycle.State.CREATED);
    }

    void doOnResume() {
        mLifecycleRegistry.setCurrentState(Lifecycle.State.RESUMED);
    }

    void doOnStart() {
        mLifecycleRegistry.setCurrentState(Lifecycle.State.STARTED);
    }

    @NonNull
    public Lifecycle getLifecycle() {
        return mLifecycleRegistry;
    }
}
