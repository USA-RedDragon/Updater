package org.lineageos.updater;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemProperties;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;

import org.lineageos.updater.controller.Controller;
import org.lineageos.updater.controller.UpdaterController;
import org.lineageos.updater.controller.UpdaterService;
import org.lineageos.updater.misc.Constants;
import org.lineageos.updater.misc.Utils;
import org.lineageos.updater.model.Update;
import org.lineageos.updater.model.UpdateBaseInfo;
import org.lineageos.updater.model.UpdateInfo;
import org.lineageos.updater.model.UpdateStatus;

import java.io.File;

public class SecretActivity extends AppCompatActivity {

    private static final String TAG = "SecretActivity";

    private UpdaterService mUpdaterService;
    private BroadcastReceiver mBroadcastReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_secret);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        mBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (UpdaterController.ACTION_UPDATE_STATUS.equals(intent.getAction())) {
                    String downloadId = intent.getStringExtra(UpdaterController.EXTRA_DOWNLOAD_ID);
                } else if (UpdaterController.ACTION_DOWNLOAD_PROGRESS.equals(intent.getAction()) ||
                        UpdaterController.ACTION_INSTALL_PROGRESS.equals(intent.getAction())) {
                    String downloadId = intent.getStringExtra(UpdaterController.EXTRA_DOWNLOAD_ID);
                } else if (UpdaterController.ACTION_UPDATE_REMOVED.equals(intent.getAction())) {
                    String downloadId = intent.getStringExtra(UpdaterController.EXTRA_DOWNLOAD_ID);
                }
            }
        };
    }

    @Override
    public void onStart() {
        super.onStart();
        Intent intent = new Intent(this, UpdaterService.class);
        startService(intent);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(UpdaterController.ACTION_UPDATE_STATUS);
        intentFilter.addAction(UpdaterController.ACTION_DOWNLOAD_PROGRESS);
        intentFilter.addAction(UpdaterController.ACTION_INSTALL_PROGRESS);
        intentFilter.addAction(UpdaterController.ACTION_UPDATE_REMOVED);
        LocalBroadcastManager.getInstance(this).registerReceiver(mBroadcastReceiver, intentFilter);
    }

    @Override
    public void onStop() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mBroadcastReceiver);
        if (mUpdaterService != null) {
            unbindService(mConnection);
        }
        super.onStop();
    }

    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            UpdaterService.LocalBinder binder = (UpdaterService.LocalBinder) service;
            mUpdaterService = binder.getService();
            final long timestamp = System.currentTimeMillis();
            Update customUpdate = new Update(new UpdateBaseInfo() {
                @Override
                public String getName() {
                    return "MANUAL UPDATE";
                }

                @Override
                public String getDownloadId() {
                    return "MANUAL";
                }

                @Override
                public long getTimestamp() {
                    return timestamp;
                }

                @Override
                public String getType() {
                    return SystemProperties.get(Constants.PROP_RELEASE_TYPE);
                }

                @Override
                public String getVersion() {
                    return SystemProperties.get(Constants.PROP_BUILD_VERSION);
                }

                @Override
                public String getDownloadUrl() {
                    return "file:///sdcard/vendor.img";
                }

                @Override
                public long getFileSize() {
                    return new File("/sdcard/vendor.img").length();
                }
            });
            customUpdate.setPersistentStatus(UpdateStatus.Persistent.VERIFIED);
            customUpdate.setFile(new File("/sdcard/vendor.img"));
            mUpdaterService.getUpdaterController().addUpdate(customUpdate);
            Utils.triggerUpdate(SecretActivity.this, "MANUAL");
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mUpdaterService = null;
        }
    };
}
