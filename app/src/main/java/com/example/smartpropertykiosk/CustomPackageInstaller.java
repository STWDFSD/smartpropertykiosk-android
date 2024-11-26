package com.example.smartpropertykiosk;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.util.Log;

import java.io.InputStream;
import java.io.OutputStream;

public class CustomPackageInstaller {

    public static void installPackage(Context context, String installSessionId, String packageName, InputStream apkStream) {

        String TAG = "Installing Application: Please wait";
        PackageManager packageManger = context.getPackageManager();
        PackageInstaller packageInstaller = packageManger.getPackageInstaller();

        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);

        params.setAppPackageName(packageName);
        PackageInstaller.Session session = null;

        try {
            Log.e(TAG, "Start");

            int sessionId = packageInstaller.createSession(params);
            session = packageInstaller.openSession(sessionId);
            OutputStream out = session.openWrite(installSessionId, 0, -1);
            byte[] buffer = new byte[1024];
            int length;
            int count = 0;
            while ((length = apkStream.read(buffer)) != -1) {
                out.write(buffer, 0, length);
                count += length;
            }
            session.fsync(out);
            out.close();

            Intent intent = new Intent(Intent.ACTION_PACKAGE_ADDED);
            session.commit(PendingIntent.getBroadcast(context, sessionId, intent, PendingIntent.FLAG_IMMUTABLE).getIntentSender());

        } catch (Exception ex) {
            Log.e(TAG, "Exception" + ex);
            ex.printStackTrace();

        } finally {
            Log.e(TAG, "Done.");
            if (session != null) {
                session.close();
            }
            PackageManager packageManager = context.getPackageManager();
            Intent intent = packageManager.getLaunchIntentForPackage(context.getPackageName());
            ComponentName componentName = intent.getComponent();
            Intent mainIntent = Intent.makeRestartActivityTask(componentName);
            context.startActivity(mainIntent);
            Runtime.getRuntime().exit(0);
        }
    }
}