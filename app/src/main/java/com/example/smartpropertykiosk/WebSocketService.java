package com.example.smartpropertykiosk;

import static com.example.smartpropertykiosk.MainActivity.output;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.DataOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okhttp3.logging.HttpLoggingInterceptor;
import okio.ByteString;

interface WebSocketCallBackInterface {
    void didreceivedKioskConfig() throws JSONException;

    void gotUserList();

    void gotAccessMessage(JSONObject objText);

    void gotAlertDialog(JSONObject objText);

    void showCallingFromInterface();

//    void createPeerConnectionFromInterface();

    void acceptCallFromInterface();

    void handleHangupFromWs();

    void createOfferForCallFromWS();

    void createOfferForVSFromWS();

    void setRemoteDescriptionForVSFromWS();

    void setRemoteDescriptionForCallFromWS();

    void handleRingingStatusFromWS();

    void restartActivity();

    void updateViewsForSipCallFromWS();
}

public class WebSocketService extends Service {

    private final IBinder myServiceBinder = new MyServiceBinder();
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    public int kioskVolume = 50;
    public String calluser = "";
    public String mNonce;
    public JSONObject kioskConfig;
    public JSONArray usersList;
    public WebSocket mWebSocket;
    public WebSocketCallBackInterface myWebSocketCallbackInterface;
    SharedPreferences sp;
    JSONObject broardcastCallDetails = null;
    boolean isCallConnected = false;
    JSONObject callingUserDetails = null;
    TimerTask task;
    private String callId = "";
    private ExecutorService executor;
    private boolean isWiegandOutputRunning = false;

    public static String randomString(int length) {
        Random generator = new Random();
        StringBuilder randomStringBuilder = new StringBuilder();
        int randomLength = generator.nextInt(length);
        char tempChar;
        for (int i = 0; i < randomLength; i++) {
            tempChar = (char) (generator.nextInt(96) + 32);
            randomStringBuilder.append(tempChar);
        }
        return randomStringBuilder.toString();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return myServiceBinder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        sp = getApplicationContext().getSharedPreferences("pcplus", MODE_PRIVATE);
        startWebSocketConnection();
        mHandler.postDelayed(fireKeepAlive, 20000);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Create a notification channel for Android 8.0 and above
        createNotificationChannel();

        // Create a notification to display while the service is running in the foreground
        Notification notification = new NotificationCompat.Builder(this, "WebSocketService")
                .setContentTitle("My Foreground Service")
                .setContentText("Service is running in foreground")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .build();

        // Start the service in foreground with the specified notification
        startForeground(1, notification);

//        // Stop the service
//        stopForeground(true);
//        stopSelf();

        return START_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel("WebSocketService",
                    "WebSocketService",
                    NotificationManager.IMPORTANCE_DEFAULT);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private void startWebSocketConnection() {
        String TAG = ":::WEBSOCKET:::";
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .addNetworkInterceptor(new HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY))
                .addInterceptor(chain -> {
                    Request.Builder builder = chain.request().newBuilder();
                    builder.addHeader("Sec-WebSocket-Protocol", "janus-protocol");
                    return chain.proceed(builder.build());
                }).connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build();
        if(sp.getString("SERVICEGROUP", "development").equals("")){
            if (myWebSocketCallbackInterface != null)
                myWebSocketCallbackInterface.restartActivity();
            else {
                Context context = getApplicationContext();
                PackageManager packageManager = context.getPackageManager();
                Intent intent = packageManager.getLaunchIntentForPackage(context.getPackageName());
                ComponentName componentName = intent.getComponent();
                Intent mainIntent = Intent.makeRestartActivityTask(componentName);
                context.startActivity(mainIntent);
                Runtime.getRuntime().exit(0);
            }
        }
        String url = "wss://elp." + sp.getString("SERVICEGROUP", "development") + ".condoplex.net:38500";
        Request request = new Request.Builder().url(url).build();
        mWebSocket = httpClient.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(@NotNull WebSocket webSocket, @NotNull Response response) {
                Log.e(TAG, "onOpen");
                Log.e(TAG, response.toString());
                createSession();
            }

            @Override
            public void onMessage(@NotNull WebSocket webSocket, @NotNull String text) {
                Log.e(TAG, "onMessage");
                Log.e(TAG, text);
                try {
                    JSONObject objText = new JSONObject(text);
//                    new PingAndLogHelper(getApplicationContext(), "log",
//                            sp.getString("SERVICEGROUP", ""),
//                            sp.getString("ELPNAME", ""),
//                            BuildConfig.VERSION_NAME,
//                            "onMessage: " + (objText.has("tag") ? objText.optString("tag") : ""),
//                            text.substring(0, Math.min(text.length(), 254)));
                    //=============================================================
                    if (objText.has("tag")) {
                        switch (objText.get("tag").toString()) {
                            case "createSession": {
                                finalizeSession(objText.get("nonce").toString());
                                break;
                            }
                            case "appAuth": {
                                if (objText.has("result") && objText.get("result").equals("success")) {
                                    addListenBroadcast();
                                    getKioskConfig();
                                }
                                break;
                            }
                            case "getConfig": {
                                if (objText.has("result") && objText.get("result").equals("success")) {
                                    kioskConfig = objText;
                                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                        if (myWebSocketCallbackInterface != null) {
                                            try {
                                                myWebSocketCallbackInterface.didreceivedKioskConfig();
                                            } catch (JSONException e) {
                                                e.printStackTrace();
                                                myWebSocketCallbackInterface.restartActivity();
                                            }
                                        } else getKioskConfig();
                                    }, 2000);
                                }
                                break;
                            }
                            case "getNames": {
                                if (objText.has("result") && objText.get("result").equals("success")) {
                                    usersList = objText.getJSONArray("list");
                                    if (myWebSocketCallbackInterface != null)
                                        myWebSocketCallbackInterface.gotUserList();
                                }
                                break;
                            }
                            case "newUser": {
                                break;
                            }
                            case "setupCall": {
                                if (task != null) {
                                    task.cancel();
                                }
                                if (objText.has("result") && objText.get("result").equals("success")) {
                                    startJanusService(objText);
                                    myWebSocketCallbackInterface.showCallingFromInterface();
                                } else {
                                    myWebSocketCallbackInterface.handleHangupFromWs();
                                }
                                break;
                            }
                        }
                    } else if (objText.has("broadcast") && objText.get("broadcast").equals("CALL") && objText.has("plugin") && objText.has("server")) {
                        startJanusService(objText);
                        myWebSocketCallbackInterface.showCallingFromInterface();
                    } else if (objText.has("broadcast") && objText.get("broadcast").equals("PROG")) {
                        switch (objText.getString("property")) {
                            case "config":
                                if (objText.getString("elp").equals(sp.getString("ELPNAME", "")))
                                    getKioskConfig();
                                break;
                            case "users":
                                getKioskConfig();
                                break;
                            case "site":
                                //TODO
                                break;
                            case "name":
                                if (objText.getString("elp").equals(sp.getString("ELPNAME", ""))) {
                                    sp.edit().putString("ELPNAME", objText.getString("newname")).apply();
                                    createSession();
                                }
                                break;
                            default:
                                break;
                        }
                    }

                    if (objText.has("broadcast") && objText.get("broadcast").equals("MESSAGE")) {
                        //TODO
                        if((objText.optString("message").equalsIgnoreCase("access granted"))) {
                            String dialCode = (callingUserDetails != null) ? callingUserDetails.optString("dialCode", "0000") : "0000";
                            sendWiegandOutput(dialCode, "dtmfFacilityCode");
                        }
                        myWebSocketCallbackInterface.gotAccessMessage(objText);
                        new Handler(Looper.getMainLooper()).postDelayed(() -> myWebSocketCallbackInterface.gotAlertDialog(objText), 2000);
                    }

                    if (objText.has("broadcast") && (objText.get("broadcast").equals("CALL") || objText.get("broadcast").equals("GUARDCALL"))
                            && objText.has("incidentId") && objText.has("incidentStatus")) {
                        broardcastCallDetails = objText;
                        if (objText.optString("incidentStatus").equals("CALLING")) {
                            myWebSocketCallbackInterface.showCallingFromInterface();
                        }
                        else if (objText.optString("incidentStatus").equals("MISSED")) {
                            myWebSocketCallbackInterface.handleHangupFromWs();
                        }
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
//                    new PingAndLogHelper(getApplicationContext(), "log",
//                            sp.getString("SERVICEGROUP", ""),
//                            sp.getString("ELPNAME", ""),
//                            BuildConfig.VERSION_NAME,
//                            "WEBSOCKET",
//                            "Got exception from backend..." + e.toString().substring(0, Math.min(e.toString().length(), 250)));
                    stopSelf();
                }
            }

            @Override
            public void onMessage(@NotNull WebSocket webSocket, @NotNull ByteString bytes) {
                Log.e(TAG, "onMessage BYTES");
            }

            @Override
            public void onClosing(@NotNull WebSocket webSocket, int code, @NotNull String reason) {
                Log.e(TAG, "onClosing");
                if (task != null) {
                    task.cancel();
                }
//                new PingAndLogHelper(getApplicationContext(), "log",
//                        sp.getString("SERVICEGROUP", ""),
//                        sp.getString("ELPNAME", ""),
//                        BuildConfig.VERSION_NAME,
//                        "WEBSOCKET",
//                        "Closing..." + code + "->" + reason);
            }

            @Override
            public void onClosed(@NotNull WebSocket webSocket, int code, @NotNull String reason) {
                Log.e(TAG, "onClosed");
                if (task != null) {
                    task.cancel();
                }
                if (myWebSocketCallbackInterface != null)
                    myWebSocketCallbackInterface.restartActivity();
            }

            @Override
            public void onFailure(@NotNull WebSocket webSocket, @NotNull Throwable t, Response response) {
//                new PingAndLogHelper(getApplicationContext(), "log",
//                        sp.getString("SERVICEGROUP", ""),
//                        sp.getString("ELPNAME", ""),
//                        BuildConfig.VERSION_NAME,
//                        "WEBSOCKET",
//                        "onFailure..." + t + "->" + response);
                if (task != null) {
                    task.cancel();
                }
                if (myWebSocketCallbackInterface != null)
                    myWebSocketCallbackInterface.restartActivity();
                else {
                    Context context = getApplicationContext();
                    PackageManager packageManager = context.getPackageManager();
                    Intent intent = packageManager.getLaunchIntentForPackage(context.getPackageName());
                    ComponentName componentName = intent.getComponent();
                    Intent mainIntent = Intent.makeRestartActivityTask(componentName);
                    context.startActivity(mainIntent);
                    Runtime.getRuntime().exit(0);
                }
            }
        });
    }

//    public void createPeerConnectionWS() {
//        myWebSocketCallbackInterface.createPeerConnectionFromInterface();
//    }

    private void startJanusService(JSONObject objText) throws JSONException {
        if (SingletonServiceManager.isMyServiceRunning) {
            return;
        } else {
            SingletonServiceManager.isMyServiceRunning = true;
        }
        if (objText.has("calluser")) {
            calluser = objText.optString("calluser");
        } else if (objText.has("phoneNumber")) {
            calluser = objText.optString("phoneNumber").replaceAll("[^0-9]", "");
        }
        if (objText.has("callId")) {
            callId = objText.optString("callId");
        }
        Intent janusService = new Intent(getApplicationContext(), JanusConnectionService.class);
        janusService.putExtra("plugin", objText.get("plugin").toString());
        janusService.putExtra("server", objText.get("server").toString());
        janusService.putExtra("port", objText.get("port").toString());
        janusService.putExtra("username", objText.get("username").toString());
        janusService.putExtra("token", objText.get("token").toString());
        //sip object
        if (objText.has("sip")) {
            janusService.putExtra("sip", objText.optString("sip"));
        }
        //phone number to call
        if (objText.has("phoneNumber")) {
            janusService.putExtra("phoneNumber", objText.optString("phoneNumber").replaceAll("[^0-9]", ""));
        } else janusService.putExtra("phoneNumber", "");
        if (objText.has("img"))
            janusService.putExtra("img", objText.get("img").toString());
        else
            janusService.putExtra("img", "");
        startService(janusService);
    }

    public void acceptCallWs() {
        myWebSocketCallbackInterface.acceptCallFromInterface();
    }

    /**
     * command: "setupCall"
     * img: "data:image/jpeg;base64,/9j/4AAQSkZJRgABAQAAAQABAA
     * tag: "CB21"
     * timeout: "1200000"
     * userId: 3
     */
    public void tryCallingUser(JSONObject objText, String img) {
        callingUserDetails = objText;


        JSONObject msg = new JSONObject();
        try {
            msg.putOpt("command", "setupCall");
            msg.putOpt("img", img);
            msg.putOpt("timeout", "1200000");
            if(objText.has("userId")) {
                msg.putOpt("userId", String.valueOf(objText.opt("userId")));
            } else if(objText.has("phoneNumber")){
                msg.putOpt("tel", String.valueOf(objText.optString("phoneNumber")));
            }
            msg.putOpt("tag", "setupCall");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        mWebSocket.send(msg.toString());
        task = new TimerTask() {
            @Override
            public void run() {
                myWebSocketCallbackInterface.handleHangupFromWs();
            }
        };
        new Timer(true).schedule(task, 15000);
    }

    public void handlehangup() {
        myWebSocketCallbackInterface.handleHangupFromWs();
    }

    public void callingUserNow() {
        myWebSocketCallbackInterface.createOfferForCallFromWS();
    }

    public void callCreateOfferForVS() {
        myWebSocketCallbackInterface.createOfferForVSFromWS();
    }

    public void callSetRemoteDescriptionForVS() {
        myWebSocketCallbackInterface.setRemoteDescriptionForVSFromWS();
    }

    public void callSetRemoteDescriptionForCall() {
        myWebSocketCallbackInterface.setRemoteDescriptionForCallFromWS();
    }

    public void handleRingingStatus() {
        myWebSocketCallbackInterface.handleRingingStatusFromWS();
    }

    private void createSession() {
        JSONObject msg = new JSONObject();
        try {
            msg.putOpt("command", "auth");
            msg.putOpt("name", sp.getString("ELPNAME", ""));
            msg.putOpt("nonce", randomString(12));
            msg.putOpt("tag", "createSession");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        mWebSocket.send(msg.toString());
    }

    private void finalizeSession(String nonce) {
        mNonce = nonce;
        JSONObject msg = new JSONObject();
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-512");
            byte[] digest = md.digest((nonce + sp.getString("SERVICEPASS", "")).getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(Integer.toString((b & 0xff) + 0x100, 16).substring(1));
            }
            msg.putOpt("command", "auth");
            msg.putOpt("name", sp.getString("ELPNAME", ""));
            msg.putOpt("pass", sb);
            msg.putOpt("tag", "appAuth");
        } catch (JSONException | NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        mWebSocket.send(msg.toString());
    }

    public void addListenBroadcast() {
        JSONObject msg = new JSONObject();
        try {
            msg.putOpt("command", "listen");
            msg.put("add", "PROG");
            msg.putOpt("tag", "listen");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        mWebSocket.send(msg.toString());
    }

    public void getKioskConfig() {
        JSONObject msg = new JSONObject();
        try {
            msg.putOpt("command", "getConfig");
            msg.putOpt("module", "ELPCONTROLLER");
            msg.putOpt("tag", "getConfig");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        mWebSocket.send(msg.toString());
    }

    public void getUsersNames() {
        JSONObject msg = new JSONObject();
        try {
            msg.putOpt("command", "getNames");
            msg.putOpt("module", "ELPCONTROLLER");
            msg.putOpt("tag", "getNames");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        mWebSocket.send(msg.toString());
    }

//    public void hangupCallBeforeAns() {
//        JSONObject msg = new JSONObject();
//        try {
//            msg.putOpt("command", "setCallStatus");
//            msg.putOpt("module", "ELPCONTROLLER");
//            msg.putOpt("tag", "setCallStatusWithIncidentId");
//            msg.putOpt("incidentId", broardcastCallDetails.opt("incidentId"));
//            msg.putOpt("status", "HANGUP");
//        } catch (JSONException e) {
//            e.printStackTrace();
//        }
//        mWebSocket.send(msg.toString());
//    }

    public void callGuard(Number timeout, JSONObject buttonData) {
        JSONObject msg = new JSONObject();
        try {
            msg.putOpt("command", "guardCall");
            msg.putOpt("timeout", timeout);
            if(buttonData != null && buttonData.optJSONArray("accessGroups") != null) {
                msg.putOpt("accessGroups", buttonData.optJSONArray("accessGroups"));
            }
            msg.putOpt("module", "ELPCONTROLLER");
            msg.putOpt("tag", "callGuard");
        } catch (JSONException e) {
            Log.e("Callguard", msg.toString());
            e.printStackTrace();
        }
        Log.e("Callguard", msg.toString());
        mWebSocket.send(msg.toString());
    }

    public void setCallStatus(String status) {
        JSONObject msg = new JSONObject();
        try {
            msg.putOpt("command", "setCallStatus");
            msg.putOpt("module", "ELPCONTROLLER");
            msg.putOpt("tag", "setCallStatus");
            msg.putOpt("status", status);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        mWebSocket.send(msg.toString());
    }

    public void getPinCodeAccess(Number pincode) {
        JSONObject msg = new JSONObject();
        try {
            msg.putOpt("command", "access");
            msg.putOpt("code", pincode);
            msg.putOpt("module", "ELPCONTROLLER");
            msg.putOpt("tag", "getPinCodeAccess");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        mWebSocket.send(msg.toString());
    }

    public void setCallbacks(WebSocketCallBackInterface callbacks) {
        myWebSocketCallbackInterface = callbacks;
    }

    public void updateViewsForSipCall() {
        myWebSocketCallbackInterface.updateViewsForSipCallFromWS();
    }

    public void handleDtmfSignal(boolean floor) {

        // for weigand output
        //TODO - need to figureout if we need this in all installation or not
        sendWiegandOutput(callingUserDetails.optString("dialCode", "0000"), "dtmfFacilityCode");

        //open door from DORRCONTROLLER
        JSONObject msg = new JSONObject();
        try {
            msg.putOpt("command", "opendoor");
            msg.putOpt("door", true);
            msg.putOpt("floor", floor);
            msg.putOpt("module", "ELPCONTROLLER");
            msg.putOpt("tag", "opendoor");
            msg.putOpt("callId", callId);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        //send to backend
        mWebSocket.send(msg.toString());

        //fire relay
        openRelay(0, 5000);
        openRelay(1, 5000);
    }

    public void sendWiegandOutput(String code, String type) {
        if (!isWiegandOutputRunning) {
            Thread thread = new Thread() {
                @Override
                public void run() {
                    isWiegandOutputRunning = true;
                    String _fcode = kioskConfig.has(type) ? kioskConfig.optString(type) : kioskConfig.optString("facilityCode", "22");
                    String _code = _fcode + ":" + code;
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
                    isWiegandOutputRunning = false;
                }
            };
            thread.setPriority(10);
            new Handler(Looper.getMainLooper()).postDelayed(thread::start, 10);
        }
    }

    public void openRelay(int relay, long time) {
        if (relay == 0) {
            output(3);
            new Handler(Looper.getMainLooper()).postDelayed(() -> output(4), time);
        } else if (relay == 1) {
            output(5);
            new Handler(Looper.getMainLooper()).postDelayed(() -> output(6), time);
        }
    }

    public void setVideoFaultCode(String command, String fault, String detail) {
        JSONObject msg = new JSONObject();
        try {
            msg.putOpt("command", "checkengine");
            msg.putOpt(command, fault);
            msg.putOpt("detail", detail);
            msg.putOpt("module", "ELPCONTROLLER");
            msg.putOpt("tag", "checkengine");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        mWebSocket.send(msg.toString());
    }

    private void keepAlive() {
        if (executor == null || executor.isShutdown()) {
            executor = Executors.newSingleThreadExecutor();
        }
        executor.submit(() -> {
//            new PingAndLogHelper(this, "ping", sp.getString("SERVICEGROUP", ""), sp.getString("ELPNAME", ""), BuildConfig.VERSION_NAME, "PING", "Just a ping...")
            try {
                new PingAndLogHelper(this, "ping", sp.getString("SERVICEGROUP", ""), sp.getString("ELPNAME", ""), BuildConfig.VERSION_NAME, "PING", "Just a ping...");
            } catch (Exception e) {
                Log.e("PingAndLog", "Error in keepAlive", e);
            }
        });
    }

    public static class SingletonServiceManager {
        public static boolean isMyServiceRunning = false;
    }

    public class MyServiceBinder extends Binder {
        public WebSocketService getService() {
            return WebSocketService.this;
        }
    }

    private final Runnable fireKeepAlive = new Runnable() {
        @Override
        public void run() {
            mHandler.postDelayed(fireKeepAlive, 120000);
            keepAlive();
        }
    };


}

