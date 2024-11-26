package com.example.smartpropertykiosk;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

public class AutoStartService extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {

        //we dont need this now. our app itself is home app
//        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
//            Intent i = new Intent(context, SplashScreenActivity.class);
//            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//            context.startActivity(i);
//        }

        if (DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) {
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/app.apk");
                try {
                    InputStream targetStream = new FileInputStream(file);
                    CustomPackageInstaller.installPackage(context, "2", "com.example.smartpropertykiosk", targetStream);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
            }, 500);
        }
    }
}
