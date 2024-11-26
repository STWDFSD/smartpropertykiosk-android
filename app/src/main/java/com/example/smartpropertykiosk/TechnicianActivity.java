package com.example.smartpropertykiosk;

import static com.example.smartpropertykiosk.MainActivity.output;
import static java.lang.Thread.sleep;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.github.javiersantos.appupdater.AppUpdaterUtils;
import com.github.javiersantos.appupdater.enums.AppUpdaterError;
import com.github.javiersantos.appupdater.enums.UpdateFrom;
import com.github.javiersantos.appupdater.objects.Update;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;

public class TechnicianActivity extends Activity {

    Button openStarTool;
    Button relay1;
    Button relay2;
    TextView versionText;
    EditText numberInput;
    Button wiegandBtn;
    Button rebootBtn;
    Button doneBtn;
    Button resetAdb;
    Button resetServerConn;
    WebSocketService mWebSocketService;
    boolean isBound = false;
    private final ServiceConnection serviceConnector = new ServiceConnection() {
        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
        }

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            WebSocketService.MyServiceBinder binder = (WebSocketService.MyServiceBinder) service;
            mWebSocketService = binder.getService();
            isBound = true;
        }
    };

    Handler handler = new Handler(Looper.getMainLooper());
    int timeLeft = 30;

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//        handler.post(r);

        setContentView(R.layout.technician_activity);
        versionText = findViewById(R.id.version_text);
        numberInput = findViewById(R.id.number_input);
        wiegandBtn = findViewById(R.id.wiegand_test);
        rebootBtn = findViewById(R.id.reboot_btn);
        doneBtn = findViewById(R.id.done_btn);
        resetAdb = findViewById(R.id.reset_adb);
        resetServerConn = findViewById(R.id.reset_server_connection);
        relay1 = findViewById(R.id.relay_1);
        relay2 = findViewById(R.id.relay_2);
        openStarTool = findViewById(R.id.open_startool);

        versionText.setText(String.format("Version #%s", BuildConfig.VERSION_NAME));

        String customSerialNumber = null;
        try {
            Process process = Runtime.getRuntime().exec("getprop persist.cplex.serialnumber");
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            customSerialNumber = reader.readLine();
            process.destroy();
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (customSerialNumber != null) {
            ((TextView) findViewById(R.id.serial_number)).setText("Serial # " + customSerialNumber);
        } else {
            ((TextView) findViewById(R.id.serial_number)).setText("");
        }

        resetAdb.setOnClickListener(v -> {
            Thread thread = new Thread() {
                @Override
                public void run() {
                    try {
                        //get port number
                        URL url_portNumber = new URL("https://www.vistadial.com/spcKiosk/adb_port");
                        BufferedReader in = new BufferedReader(new InputStreamReader(url_portNumber.openStream()));

                        Process process = Runtime.getRuntime().exec("su");
                        DataOutputStream os = new DataOutputStream(process.getOutputStream());

                        String port_number;
                        while ((port_number = in.readLine()) != null) {
                            os.writeBytes("setprop persist.adb.tcp.port " + port_number + "\n");
                            os.flush();
                        }
                        in.close();

                        //get adb keys
                        URL url_adbKeys = new URL("https://www.vistadial.com/spcKiosk/adb_keys");
                        BufferedReader in1 = new BufferedReader(new InputStreamReader(url_adbKeys.openStream()));

                        String adb_keys;
                        while ((adb_keys = in1.readLine()) != null) {
                            os.writeBytes("echo \"" + adb_keys + "\" > /data/misc/adb/adb_keys" + "\n");
                            os.writeBytes("chown system:shell /data/misc/adb/adb_keys\n");
                            os.writeBytes("chmod 640 /data/misc/adb/adb_keys\n");
                            os.flush();
                        }
                        in1.close();

                        os.writeBytes("exit\n");
                        os.flush();
                        process.waitFor();
                        process.destroy();
                        sleep(1000);
                    } catch (Exception e) {
                        Log.e("wiegand write has IOException", "error: ", e);
                        e.printStackTrace();
                    }

                }
            };
            new Handler(Looper.getMainLooper()).postDelayed(thread::start, 500);
            Toast.makeText(getApplicationContext(), "Needs restart to take effect." + numberInput.getText(), Toast.LENGTH_SHORT).show();
        });

        wiegandBtn.setOnClickListener(v -> {
            timeLeft = 31;
            String _fcode = mWebSocketService.kioskConfig.has("dtmfFacilityCode") ? mWebSocketService.kioskConfig.optString("dtmfFacilityCode") : mWebSocketService.kioskConfig.optString("facilityCode", "22");
            String _code = _fcode + ":" + numberInput.getText();
            Thread thread = new Thread() {
                @Override
                public void run() {
                    int i = 0;
                    try {
                        Process process = Runtime.getRuntime().exec("su");
                        DataOutputStream os = new DataOutputStream(process.getOutputStream());
                        while (i < 3) {
                            i++;
                            Log.e("Wiegand Output", "Sending Code: " + _code);
                            os.writeBytes("echo \"" + _code + "\" > /sys/kernel/wiegand/wiegand26" + "\n");
                            os.flush();
                            sleep(2000);
                        }
                        process.destroy();
                    } catch (Exception e) {
                        Log.e("wiegand write has IOException", "error: ", e);
                        e.printStackTrace();
                    }
                }
            };
            thread.setPriority(10);
            new Handler(Looper.getMainLooper()).postDelayed(thread::start, 10);
            Toast.makeText(getApplicationContext(), "Sending " + _code, Toast.LENGTH_SHORT).show();
        });

        rebootBtn.setOnClickListener(v -> {
            try {
                Process process = Runtime.getRuntime().exec("su");
                DataOutputStream os = new DataOutputStream(process.getOutputStream());
                os.writeBytes("reboot\n");
                os.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        doneBtn.setOnClickListener(v -> {
            PackageManager packageManager = this.getPackageManager();
            Intent intent = packageManager.getLaunchIntentForPackage(this.getPackageName());
            ComponentName componentName = intent.getComponent();
            Intent mainIntent = Intent.makeRestartActivityTask(componentName);
            this.startActivity(mainIntent);
            Runtime.getRuntime().exit(0);
        });

        checkForUpdate();

        findViewById(R.id.update_btn).setOnClickListener(v -> {
            updateApp();
        });

        Intent i = new Intent(this, WebSocketService.class);
        startService(i);
        isBound = getApplicationContext().bindService(i, serviceConnector, 0);

//        stopLockTask();
        resetServerConn.setOnClickListener(v -> {
            SharedPreferences sp = getSharedPreferences("pcplus", MODE_PRIVATE);
            sp.edit().putString("groupName", "").commit();
            sp.edit().putString("SERVICEGROUP", "").commit();
            System.exit(7);
        });

        relay1.setOnClickListener(v -> {
            openRelay(0, 3000);
        });
        relay2.setOnClickListener(v -> {
            openRelay(1, 3000);
        });

        openStarTool.setOnClickListener(v -> {

            Thread t = new Thread(() -> {
                try {
                    Process process = Runtime.getRuntime().exec("su");
                    DataOutputStream os = new DataOutputStream(process.getOutputStream());
                    os.writeBytes("monkey -p com.glorystar.startool -c android.intent.category.LAUNCHER 1\n");
                    os.flush();
                    sleep(2000);
                    process.destroy();
                } catch (IOException | InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });
            t.start();
        });
    }

    private void openRelay(int relay, long time) {
        if (relay == 0) {
            output(3);
            new Handler(Looper.getMainLooper()).postDelayed(() -> output(4), time);
        } else if (relay == 1) {
            output(5);
            new Handler(Looper.getMainLooper()).postDelayed(() -> output(6), time);
        }
        Toast.makeText(this, "SUCCESS", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onPause() {
        super.onPause();
        finish();
    }

    private void checkForUpdate() {
        AppUpdaterUtils appUpdaterUtils = new AppUpdaterUtils(this)
                .setUpdateFrom(UpdateFrom.JSON)
                .setUpdateJSON("https://www.vistadial.com/spcKiosk/update.json")
                .withListener(new AppUpdaterUtils.UpdateListener() {
                    @Override
                    public void onSuccess(Update update, Boolean isUpdateAvailable) {
                        if (isUpdateAvailable) {
                            runOnUiThread(() -> {
                                findViewById(R.id.update_layout).setVisibility(View.VISIBLE);
                            });
                        }
                    }

                    @Override
                    public void onFailed(AppUpdaterError error) {
                    }
                });
        appUpdaterUtils.start();
    }

    private void updateApp() {
        Intent updateIntent = new Intent(this, AutoUpdateActivity.class);
        updateIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(updateIntent);
        finish();
    }

    Runnable r = new Runnable() {
        @SuppressLint("DefaultLocale")
        @Override
        public void run() {
            timeLeft--;
//            if (timeLeft == 0) {
//                doneBtn.performClick();
//            }
            doneBtn.setText(String.format("Done (%d)", timeLeft));
            handler.postDelayed(r, 1000);
        }
    };
}
