package com.nexradnow.android.model;

/**
 * Created by hobsonm on 9/15/15.
 */
public class AppMessage {
    protected String message;

    public enum Type {ERROR,INFO,PROGRESS};

    protected Type type;
    public AppMessage(String message, Type type) {
        this.message = message;
        this.type = type;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Type getType() {
        return type;
    }

}
