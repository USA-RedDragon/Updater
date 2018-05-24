package org.lineageos.updater.controller;

import android.util.Log;

public class SystemFlasher {

    private static final String TAG = "SystemFlasher";

    public static final int SUCCESS = 0;
    public static final int FLASHING = 1;
    public static final int SYNCING = 2;
    public static final int FINISHED_NEEDS_REBOOT = 3;
    public static final int IDLE = 4;

    public interface SystemFlasherCallback {
        void onStatusUpdate(int status, int percent);
        void onFinished(boolean error);
    }

    private static SystemFlasher mInstance;
    private SystemFlasherCallback mCallback;

    public boolean bind(SystemFlasherCallback callback) {
        this.mCallback = callback;
        return true;
    }

    public static SystemFlasher getInstance() {
        Log.d(TAG, "Getting SystemFlasher");
        if(mInstance == null) {
            mInstance = new SystemFlasher();
        }
        return mInstance;
    }

    public void flash(String filePath) {

    }
}
