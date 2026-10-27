package com.vinz.moba;

public interface Platform {
    interface Callback { void done(String name); }
    boolean hasGoogle();
    void googleSignIn(Callback cb);
}
