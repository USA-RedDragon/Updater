/*
 * Copyright (C) 2017 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.lineageos.updater.controller;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.v7.preference.PreferenceManager;
import android.util.Log;

import org.lineageos.updater.misc.Constants;
import org.lineageos.updater.model.Update;
import org.lineageos.updater.model.UpdateStatus;

import java.io.File;

class TrebleUpdateInstaller {

    private static final String TAG = "TrebleUpdateInstaller";

    private static final String PREF_INSTALLING_TREBLE_ID = "installing_treble_id";

    private static TrebleUpdateInstaller sInstance = null;

    private final UpdaterController mUpdaterController;
    private final Context mContext;
    private String mDownloadId;

    private boolean mBound;
    private SystemFlasher flasher;

    private final SystemFlasher.SystemFlasherCallback mSystemFlasherCallback = new SystemFlasher.SystemFlasherCallback() {

        @Override
        public void onStatusUpdate(int status, int percent) {
            Update update = mUpdaterController.getActualUpdate(mDownloadId);
            if (update == null) {
                // We read the id from a preference, the update could no longer exist
                installationDone(status == SystemFlasher.SUCCESS);
                return;
            }

            switch (status) {
                case SystemFlasher.FLASHING:
                case SystemFlasher.SYNCING: {
                    if (update.getStatus() != UpdateStatus.INSTALLING) {
                        update.setStatus(UpdateStatus.INSTALLING);
                        mUpdaterController.notifyUpdateChange(mDownloadId);
                    }
                    mUpdaterController.getActualUpdate(mDownloadId).setInstallProgress(percent);
                    boolean syncing = status == SystemFlasher.SYNCING;
                    mUpdaterController.getActualUpdate(mDownloadId).setFinalizing(syncing);
                    mUpdaterController.notifyInstallProgress(mDownloadId);
                }
                break;

                case SystemFlasher.FINISHED_NEEDS_REBOOT: {
                    installationDone(true);
                    update.setInstallProgress(0);
                    update.setStatus(UpdateStatus.INSTALLED);
                    mUpdaterController.notifyUpdateChange(mDownloadId);
                    SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(
                            mContext);
                    boolean deleteUpdate = pref.getBoolean(Constants.PREF_AUTO_DELETE_UPDATES,
                            false);
                    if (deleteUpdate) {
                        mUpdaterController.deleteUpdate(mDownloadId);
                    }
                }
                break;

                case SystemFlasher.IDLE: {
                    // The service was restarted because we thought we were installing an
                    // update, but we aren't, so clear everything.
                    installationDone(false);
                }
                break;
            }
        }

        @Override
        public void onFinished(boolean error) {
            if (error) {
                installationDone(false);
                Update update = mUpdaterController.getActualUpdate(mDownloadId);
                update.setInstallProgress(0);
                update.setStatus(UpdateStatus.INSTALLATION_FAILED);
                mUpdaterController.notifyUpdateChange(mDownloadId);
            }
        }
    };

    static synchronized boolean isInstallingUpdate(Context context) {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
        return pref.getString(TrebleUpdateInstaller.PREF_INSTALLING_TREBLE_ID, null) != null ||
                pref.getBoolean(Constants.PREF_NEEDS_REBOOT, false);
    }

    static synchronized boolean isInstallingUpdate(Context context, String downloadId) {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
        return downloadId.equals(pref.getString(TrebleUpdateInstaller.PREF_INSTALLING_TREBLE_ID, null)) ||
                pref.getBoolean(Constants.PREF_NEEDS_REBOOT, false);
    }

    private TrebleUpdateInstaller(Context context, UpdaterController updaterController) {
        mUpdaterController = updaterController;
        mContext = context.getApplicationContext();
        flasher = SystemFlasher.getInstance();
    }

    static synchronized TrebleUpdateInstaller getInstance(Context context,
                                                          UpdaterController updaterController) {
        if (sInstance == null) {
            sInstance = new TrebleUpdateInstaller(context, updaterController);
        }
        return sInstance;
    }

    public boolean install(String downloadId) {
        if (isInstallingUpdate(mContext)) {
            Log.e(TAG, "Already installing an update");
            return false;
        }

        mDownloadId = downloadId;

        File file = mUpdaterController.getActualUpdate(mDownloadId).getFile();
        if (!file.exists()) {
            Log.e(TAG, "The given update doesn't exist");
            mUpdaterController.getActualUpdate(downloadId)
                    .setStatus(UpdateStatus.INSTALLATION_FAILED);
            mUpdaterController.notifyUpdateChange(downloadId);
            return false;
        }

        if (!mBound) {
            mBound = flasher.bind(mSystemFlasherCallback);
            if (!mBound) {
                Log.e(TAG, "Could not bind");
                mUpdaterController.getActualUpdate(downloadId)
                        .setStatus(UpdateStatus.INSTALLATION_FAILED);
                mUpdaterController.notifyUpdateChange(downloadId);
                return false;
            }
        }

        flasher.flash(file.getAbsolutePath());

        mUpdaterController.getActualUpdate(mDownloadId).setStatus(UpdateStatus.INSTALLING);
        mUpdaterController.notifyUpdateChange(mDownloadId);

        PreferenceManager.getDefaultSharedPreferences(mContext).edit()
                .putString(PREF_INSTALLING_TREBLE_ID, mDownloadId)
                .apply();

        return true;
    }

    public boolean reconnect() {
        if (!isInstallingUpdate(mContext)) {
            Log.e(TAG, "reconnect: Not installing any update");
            return false;
        }

        if (mBound) {
            return true;
        }

        mDownloadId = PreferenceManager.getDefaultSharedPreferences(mContext)
                .getString(PREF_INSTALLING_TREBLE_ID, null);

        // We will get a status notification as soon as we are connected
        mBound = flasher.bind(mSystemFlasherCallback);
        if (!mBound) {
            Log.e(TAG, "Could not bind");
            return false;
        }

        return true;
    }

    private void installationDone(boolean needsReboot) {
        PreferenceManager.getDefaultSharedPreferences(mContext).edit()
                .putBoolean(Constants.PREF_NEEDS_REBOOT, needsReboot)
                .remove(PREF_INSTALLING_TREBLE_ID)
                .apply();
    }

    public boolean cancel() {
        // You __really__ don't want to cancel a direct flash
        return false;
    }
}
