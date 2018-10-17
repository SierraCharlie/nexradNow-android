package com.nexradnow.android.modules;

import javax.inject.Provider;

public class SingletonProvider<T> implements Provider<T> {

    protected T singleton;
    protected Initializer<T> initializer;
    protected Class<T> singletonClass;
    SingletonProvider(Class<T> singletonClass) {
        this.singletonClass = singletonClass;
    }

    SingletonProvider(Class<T> singletonClass, Initializer<T> initializer) {
        this.initializer = initializer;
        this.singletonClass = singletonClass;
    }

    @Override
    public T get() {
        if (singleton == null) {
            try {
                singleton = singletonClass.newInstance();

            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
            if (initializer != null) {
                initializer.initialize(singleton);
            }
        }
        return singleton;
    }

}
