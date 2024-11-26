package com.example.smartpropertykiosk;

import static com.example.smartpropertykiosk.MainActivity.output;

import android.Manifest;
import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;

import java.io.DataOutputStream;
import java.io.IOException;

public class SplashScreenActivity extends Activity {


    DevicePolicyManager dpm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        //global exception handler
        Thread.setDefaultUncaughtExceptionHandler((thread, e) -> {
            if (e instanceof OutOfMemoryError || e instanceof SecurityException) {
                try (DataOutputStream os = new DataOutputStream(Runtime.getRuntime().exec("su").getOutputStream())) {
                    os.writeBytes("reboot\n");
                    os.flush();
                } catch (IOException ex) {
                    ex.printStackTrace();
                    System.exit(1);
                }
            } else {
                e.printStackTrace();
                System.exit(2);
            }
        });

        output(4);
        output(6);
        super.onCreate(savedInstanceState);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.KEEP_SCREEN_ON
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN);

        Context context = getApplicationContext();
        final String[] APP_PACKAGES = {"com.example.smartpropertykiosk", "net.christianbeier.droidvnc_ng"};
        dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName adminName = new ComponentName(this, MyDeviceAdminReceiver.class);

        dpm.setLockTaskPackages(adminName, APP_PACKAGES);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            dpm.setLockTaskFeatures(adminName,
                    DevicePolicyManager.LOCK_TASK_FEATURE_NONE);
        }
        String packageName = "com.example.smartpropertykiosk";
        int permissionCode = DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED;
        dpm.setPermissionGrantState(adminName, packageName, Manifest.permission.CAMERA, permissionCode);
        dpm.setPermissionGrantState(adminName, packageName, Manifest.permission.RECORD_AUDIO, permissionCode);
        dpm.setPermissionGrantState(adminName, packageName, Manifest.permission.READ_PHONE_STATE, permissionCode);
        dpm.setPermissionGrantState(adminName, packageName, Manifest.permission.READ_EXTERNAL_STORAGE, permissionCode);
        dpm.setPermissionGrantState(adminName, packageName, Manifest.permission.WRITE_EXTERNAL_STORAGE, permissionCode);
        dpm.setPermissionGrantState(adminName, packageName, Manifest.permission.SET_TIME_ZONE, permissionCode);
        dpm.setPermissionGrantState(adminName, packageName, Manifest.permission.ACCESS_NETWORK_STATE, permissionCode);
        dpm.setPermissionGrantState(adminName, packageName, Manifest.permission.WAKE_LOCK, permissionCode);

//        searchForServer();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
//        if (hasFocus && BuildConfig.IS_PRODUCTION && dpm.isLockTaskPermitted(getPackageName())) {
//            try {
//                Log.e("SplashScreenActivity", "onFocus - Starting lock task");
//                startLockTask();
//            } catch (Exception e) {
//                rebootDevice();
//            }
//        }
    }

//    @Override
//    protected void onResume() {
//        super.onResume();
//        if(BuildConfig.IS_PRODUCTION && dpm.isLockTaskPermitted(getPackageName())) {
//            try {
//                Log.e("SplashScreenActivity", "onResume - Starting lock task");
//                startLockTask();
//            } catch (Exception e) {
//                Log.e("onResume", "Can not start Lock Task.\n" + e);
//            }
//        }
//    }

    private void rebootDevice() {
        Log.e("SplashScreen", "rebootDevice called.");
//        try (DataOutputStream os = new DataOutputStream(Runtime.getRuntime().exec("su").getOutputStream())) {
//            os.writeBytes("reboot\n");
//            os.flush();
//        } catch (IOException e) {
//            e.printStackTrace();
//            new Handler(Looper.getMainLooper()).postDelayed(this::rebootDevice, 5000);
//        }
    }

    private void searchForServer() {
        SharedPreferences sp = getSharedPreferences("pcplus", MODE_PRIVATE);
        sp.edit().putBoolean("isUpdateChecked", false).apply();
        sp.edit().remove("localIp").apply();
        sp.edit().remove("serialNo").apply();

        long currentTime = System.currentTimeMillis();
        long lastRestartTime = sp.getLong("lastRestartTime", 0);

        if (shouldReboot(currentTime, lastRestartTime)) {
            rebootDevice();
        } else {
            updateLastRestartTime(sp, currentTime);
        }

        if (sp.contains("SERVICEGROUP") && !sp.getString("SERVICEGROUP", "").isEmpty()) {
            Intent i = new Intent(this, StartupActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } else {
            new Thread(new UdpClient(this)).start();
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                Intent i = new Intent(this, StartupActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
            }, 3000);
        }
    }

    private static final long REBOOT_THRESHOLD = 10 * 1000;
    private boolean shouldReboot(long currentTime, long lastRestartTime) {
        return currentTime - lastRestartTime < REBOOT_THRESHOLD;
    }

    private void updateLastRestartTime(SharedPreferences sp, long currentTime) {
        SharedPreferences.Editor editor = sp.edit();
        editor.putLong("lastRestartTime", currentTime);
        editor.apply();
    }
}
