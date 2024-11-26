package com.example.smartpropertykiosk;

import static android.content.Context.MODE_PRIVATE;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import com.android.volley.RequestQueue;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

public class PingAndLogHelper {

    private static final String LOG_URL = "https://www.vistadial.com/spcKiosk/weblogs/log.php";
    private static final String PING_URL = "https://www.vistadial.com/spcKiosk/weblogs/ping.php";
    private final Context context;
    private final String workToDo;
    SharedPreferences sp1;

    private String tag = "Just a PING";

    public PingAndLogHelper(Context context, String workTodo, String... strings) {
        this.context = context;
        this.workToDo = workTodo;
//        if (workToDo.equals("log")) return;
        sp1 = context.getSharedPreferences("pcplus", MODE_PRIVATE);
        doInBackground(strings);
        Log.e(tag, "Started");
    }

    public String getLocalIpAddress() {
        Log.e(tag, "Getting local IP");
        if (sp1.contains("localIp")) {
            return sp1.getString("localIp", "");
        }
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements(); ) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements(); ) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address) {
                        sp1.edit().putString("localIp", inetAddress.getHostAddress()).apply();
                        return inetAddress.getHostAddress();
                    }
                }
            }
        } catch (SocketException ex) {
            ex.printStackTrace();
        }
        return "";
    }

    private String getSerialNumber() {
        Log.e(tag, "Getting Serial Number");
        if (sp1.contains("serialNo")) {
            return sp1.getString("serialNo", "");
        } else {
            String serialNo = "";
            Process process;
            BufferedReader reader;

            try {
                // Execute the first command
                process = Runtime.getRuntime().exec("getprop persist.cplex.serialnumber");
                reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                serialNo += reader.readLine();
                process.waitFor(); // Wait for the process to finish before moving on
                reader.close();

                // Execute the second command
                process = Runtime.getRuntime().exec("getprop ro.serialno");
                reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                serialNo += "<=>" + reader.readLine();
                process.waitFor(); // Wait for the process to finish before moving on
                reader.close();
                process.destroy();

                sp1.edit().putString("serialNo", serialNo).apply();
                return serialNo;
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        }
        return "";
    }

    public int getTemperature() {
        Log.e(tag, "Reading temperature");
        int temperature = 0;
        Process process;
        BufferedReader reader;
        try {
            // Execute the first command
            process = Runtime.getRuntime().exec("cat sys/class/thermal/thermal_zone0/temp");
            reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            temperature = Integer.parseInt(reader.readLine());
            process.waitFor(); // Wait for the process to finish before moving on
            reader.close();
            process.destroy();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }

        int x = sp1.getInt("temperature", 85);
        Log.e(tag, "Actual temperature " + temperature);
        Log.e(tag, "Stored temperature " + x);
        if (temperature / 1000 >= x) {
            sp1.edit().putInt("temperature", temperature / 1000 + 15).apply();
            Log.e(tag, "Too hot, will reboot in 2 seconds.");
            new Handler(Looper.getMainLooper()).postDelayed(this::rebootDevice, 2000);
        } else if (x - temperature / 1000 > 10) {
            sp1.edit().putInt("temperature", Math.max(temperature / 1000 + 15, 70)).apply();
        }
        return temperature / 1000;
    }

    public int getMemoryUsage() {
        Log.e(tag, "Reading memory usage");
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(memoryInfo);
        if (memoryInfo.lowMemory) {
            Log.e(tag, "low memory. need to reboot now");
            rebootDevice();
        }

        final Runtime runtime = Runtime.getRuntime();
        return (int) (((runtime.totalMemory() - runtime.freeMemory())) * 100 / runtime.maxMemory());
    }

    private void rebootDevice() {
        try (DataOutputStream os = new DataOutputStream(Runtime.getRuntime().exec("su").getOutputStream())) {
            os.writeBytes("reboot\n");
            os.flush();
        } catch (IOException e) {
            e.printStackTrace();
            new Handler(Looper.getMainLooper()).postDelayed(this::rebootDevice, 5000);
        }
    }

    private void doInBackground(String... strings) {
        String sitename = strings[0];
        String serialNo = getSerialNumber();
        String kioskname = strings[1];
        String appversion = strings[2];
        String localip = getLocalIpAddress();
        String message = strings[3];
        String details = strings[4];

        String url = (workToDo.equals("log") ? LOG_URL : PING_URL) + "?sitename=" + sitename + "&serialno=" + serialNo + "&kioskname=" + kioskname + "&appversion=" + appversion + "&localip=" + localip + "&message=" + message + "&details=" + details + "&uptime=" + SystemClock.elapsedRealtime();

        if (workToDo.equals("ping")) {
            int x = getMemoryUsage();
            url += "&memory=" + x;

            if (x > 70) {
                try {
                    System.exit(4);
                } catch (Exception e) {
                    rebootDevice();
                }
            }

            int y = getTemperature();
            url += "&temperature=" + y;
        }
        Log.e(tag, "Sending request now");
        sendRequest(url);
    }

    private void sendRequest(String url) {
        RequestQueue requestQueue = Volley.newRequestQueue(context);
        StringRequest stringRequest = new StringRequest(0, url, response -> {
            Log.e(tag, "Got response " + response);
            if (response != null) {
                switch (response) {
                    case "Reboot":
                        rebootDevice();
                        break;
                    case "Update":
                        Intent updateIntent = new Intent(context, AutoUpdateActivity.class);
                        updateIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(updateIntent);
                        break;
                    case "Restart":
                        System.exit(5);
                        break;
                    default:
                        break;
                }
                requestQueue.stop();
            }
        }, error -> {
            Log.e(tag, "Error in string request");
            // stop the request queue if there is an error
            requestQueue.stop();
        });
        requestQueue.add(stringRequest);
        new Handler(Looper.getMainLooper()).postDelayed(requestQueue::stop, 10000);
    }
}
