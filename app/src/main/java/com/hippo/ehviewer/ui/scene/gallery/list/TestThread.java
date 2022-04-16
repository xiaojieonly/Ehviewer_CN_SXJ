package com.hippo.ehviewer.ui.scene.gallery.list;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

public class TestThread extends Thread{

    @Override
    public synchronized void start() {
        super.start();

    }

    @Override
    public void run() {
        super.run();
        UiDevice uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        uiDevice.pressHome();
    }


}
