package com.cleanbrowser.app;

import android.app.Application;

public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        AdBlocker blocker = AdBlocker.get(this);
        blocker.loadAsync();
        blocker.updateIfDue();
    }
}
