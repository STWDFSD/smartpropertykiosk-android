package com.example.smartpropertykiosk;

import android.Manifest;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;

import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.SessionDescription;

import java.math.BigInteger;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okhttp3.logging.HttpLoggingInterceptor;
import okio.ByteString;

public class JanusConnectionService extends Service {

    private static final String TAG = ":::JanusWebSocket:::";
    private final ConcurrentHashMap<String, JanusTransaction> transactions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<BigInteger, JanusHandle> handles = new ConcurrentHashMap<>();

    @Nullable
    private final IBinder myBinder = new MyLocalBinder();
    public JSONObject myJsep;
    public Boolean isIncomingCall = false;
    public String myImg;
    public String myPlugin;
    WebSocketService myWebSocketService;
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
            myWebSocketService = binder.getService();
            isBound = true;
        }
    };
    private BigInteger mSessionId;
    private Handler mHandler;
    private JanusHandle myHandle;
    private WebSocket mJanusWebSocket;
    private String myServer;
    private String myPort;
    private String myUsername;
    private String myToken;
    private JSONObject sip;
    private String phoneNumber;
    private Boolean isCallingUser = false;
    private Boolean isDoorOpened = false;
    private Boolean isFloorOpened = false;

    @Override
    public IBinder onBind(Intent intent) {
        return myBinder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        myPlugin = intent.getStringExtra("plugin");
        myServer = intent.getStringExtra("server");
        myPort = intent.getStringExtra("port");
        myUsername = intent.getStringExtra("username");
        myToken = intent.getStringExtra("token");
        myImg = intent.getStringExtra("img");
        if (intent.hasExtra("sip")) {
            try {
                sip = new JSONObject(intent.getStringExtra("sip"));
            } catch (JSONException e) {
                e.printStackTrace();
                detachJanusPlugin();
            }
        }

        phoneNumber = intent.getStringExtra("phoneNumber");
        startButtonClicked();

        return START_NOT_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (!isIncomingCall && !isCallingUser) {
                Log.e("tag", ":::::STOPPING SERVICE:::::");
                detachJanusPlugin();
            }
        }, 5000);

        Intent webSocketSer = new Intent(this, WebSocketService.class);
        isBound = getApplicationContext().bindService(webSocketSer, serviceConnector, 0);
    }

    private void startButtonClicked() {
        mHandler = new Handler(Looper.getMainLooper());
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .addNetworkInterceptor(new HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY))
                .addInterceptor(chain -> {
                    Request.Builder builder = chain.request().newBuilder();
                    builder.addHeader("Sec-WebSocket-Protocol", "janus-protocol");
                    return chain.proceed(builder.build());
                }).connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build();
        String url = "wss://" + this.myServer + ":" + this.myPort;
        Request request = new Request.Builder().url(url).build();
        mJanusWebSocket = httpClient.newWebSocket(request, new WebSocketListener() {
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
                onMessage1(text);
            }

            @Override
            public void onMessage(@NotNull WebSocket webSocket, @NotNull ByteString bytes) {
                Log.e(TAG, "onMessage");
            }

            @Override
            public void onClosing(@NotNull WebSocket webSocket, int code, @NotNull String reason) {
                Log.e(TAG, "onClosing");
            }

            @Override
            public void onClosed(@NotNull WebSocket webSocket, int code, @NotNull String reason) {
                Log.e(TAG, "onClosed");
            }

            @Override
            public void onFailure(@NotNull WebSocket webSocket, @NotNull Throwable t, Response response) {
                Log.e(TAG, "onFailure" + t);
                Context context = getApplicationContext();
                Intent mainIntent = Intent.makeRestartActivityTask(context.getPackageManager().getLaunchIntentForPackage(context.getPackageName()).getComponent());
                context.startActivity(mainIntent);
                Runtime.getRuntime().exit(0);
            }
        });
    }

    private void createSession() {
        String transaction = randomString();
        JanusTransaction jt = new JanusTransaction();
        jt.tid = transaction;
        jt.success = jo -> {
            mSessionId = new BigInteger(Objects.requireNonNull(jo.optJSONObject("data")).optString("id"));
            mHandler.post(fireKeepAlive);
            connectVideoPlugin();
        };
        jt.error = jo -> {
        };
        transactions.put(transaction, jt);
        JSONObject msg = new JSONObject();
        try {
            msg.putOpt("janus", "create");
            msg.putOpt("token", myToken);
            msg.putOpt("transaction", transaction);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        mJanusWebSocket.send(msg.toString());
    }

    private void connectVideoPlugin() {
        String transaction = randomString();
        JanusTransaction jt = new JanusTransaction();
        jt.tid = transaction;
        jt.success = jo -> {
            JanusHandle janusHandle = new JanusHandle();
            janusHandle.handleId = new BigInteger(Objects.requireNonNull(jo.optJSONObject("data")).optString("id"));
            janusHandle.onJoined = jh -> {
            };
            janusHandle.onRemoteJsep = (jh, jsep) -> {
            };
            handles.put(janusHandle.handleId, janusHandle);
            registerUser(janusHandle);
        };
        jt.error = jo -> {
        };
        transactions.put(transaction, jt);
        JSONObject msg = new JSONObject();
        try {
            msg.putOpt("janus", "attach");
            msg.putOpt("token", myToken);
            msg.putOpt("plugin", myPlugin);
            msg.putOpt("transaction", transaction);
            msg.putOpt("session_id", mSessionId);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        mJanusWebSocket.send(msg.toString());
    }

    private void registerUser(JanusHandle handle) {
        JSONObject msg = new JSONObject();
        JSONObject body = new JSONObject();
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_NUMBERS) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            detachJanusPlugin();
            return;
        }

        try {
            if (myPlugin.contains("videocall")) {
                body.putOpt("request", "register");
                body.putOpt("username", myUsername);
                body.putOpt("token", myToken);
            } else if (myPlugin.contains("sip")) {
                body.putOpt("request", "register");
                body.putOpt("username", "sip:" + sip.optString("sipUser") + "@" + sip.optString("sipServer"));
                body.putOpt("secret", sip.optString("sipSecret"));

                SharedPreferences sp = getApplicationContext().getSharedPreferences("pcplus", MODE_PRIVATE);
                body.putOpt("display_name", sp.getString("ELPNAME", "Entryphone") + " " + sp.getString("SERVICEGROUP", ""));
            }
            msg.putOpt("janus", "message");
            msg.putOpt("token", myToken);
            msg.putOpt("body", body);
            msg.putOpt("transaction", randomString());
            msg.putOpt("session_id", mSessionId);
            msg.putOpt("handle_id", handle.handleId);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        mJanusWebSocket.send(msg.toString());
    }

    private void keepAlive() {
        String transaction = randomString();
        JSONObject msg = new JSONObject();
        try {
            msg.putOpt("janus", "keepalive");
            msg.putOpt("token", myToken);
            msg.putOpt("session_id", mSessionId);
            msg.putOpt("transaction", transaction);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        mJanusWebSocket.send(msg.toString());
    }

    private String randomString() {
        final String str = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        final Random rnd = new Random();
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            sb.append(str.charAt(rnd.nextInt(str.length())));
        }
        return sb.toString();
    }

    private void onMessage1(String message) {
        try {
            JSONObject jo = new JSONObject(message);
            String janus = jo.optString("janus");
            switch (janus) {
                case "success": {
                    String transaction = jo.optString("transaction");
                    JanusTransaction jt = transactions.get(transaction);
                    if (Objects.requireNonNull(jt).success != null) {
                        jt.success.success(jo);
                    }
                    transactions.remove(transaction);
                    break;
                }
                case "error": {
                    String transaction = jo.optString("transaction");
                    JanusTransaction jt = transactions.get(transaction);
                    if (Objects.requireNonNull(jt).error != null) {
                        jt.error.error(jo);
                    }
                    transactions.remove(transaction);
                    detachJanusPlugin();
                    break;
                }
                case "ack":
                    Log.e(TAG, "Just an ack");
                    break;
                case "webrtcup":
                    //if not up in 4 seconds, hangup and try again - TODO
                    break;
                default:
                    JanusHandle handle = handles.get(new BigInteger(jo.optString("sender")));
                    if (handle == null) {
                        Log.e(TAG, "missing handle");
                    } else if (janus.equals("event")) {

                        if (Objects.requireNonNull(Objects.requireNonNull(jo.optJSONObject("plugindata")).optJSONObject("data")).has("error")) {
                            detachJanusPlugin();
                            return;
                        }

                        JSONObject plugin = Objects.requireNonNull(Objects.requireNonNull(jo.optJSONObject("plugindata")).optJSONObject("data")).optJSONObject("result");
                        if (Objects.requireNonNull(plugin).optString("event").equals("registered")) {
                            handle.onJoined.onJoined(handle);
                            if (!myWebSocketService.calluser.equals("")) {
                                if (myPlugin.contains("videocall"))
                                    new Handler(Looper.getMainLooper()).postDelayed(this::getUserList, 1000);
                                else if (myPlugin.contains("sip")) {
                                    isCallingUser = true;
                                    myWebSocketService.callingUserNow();
                                }
                            }
                        } else if (plugin.has("list")) {
                            JSONArray list = plugin.optJSONArray("list");
                            assert list != null;
                            if (list.toString().contains(myWebSocketService.calluser)) {
                                isCallingUser = true;
                                myWebSocketService.callingUserNow();
                            } else {
                                new Handler(Looper.getMainLooper()).postDelayed(this::getUserList, 1000);
                            }
                        } else if (plugin.optString("event").equals("incomingcall")) {
                            Log.e(":::Incoming call:::", "");
                            this.createIncomingCallNotification();
                        } else if (plugin.optString("event").equals("hangup")) {
                            detachJanusPlugin();
                        } else if (plugin.optString("event").equals("calling")) {
                            myWebSocketService.handleRingingStatus();
                        } else if (plugin.optString("event").equals("accepted") && myPlugin.contains("sip")) {
                            myWebSocketService.updateViewsForSipCall();
                        } else if (plugin.optString("event").equals("info")) {
                            String number = plugin.optString("content").replaceAll("[^0-9]", "").substring(0, 1);
                            String dtmfOpenDoor = myWebSocketService.kioskConfig.has("dtmfOpenDoor") ? myWebSocketService.kioskConfig.optString("dtmfOpenDoor") : "9";
                            String dtmfReleaseElevator = myWebSocketService.kioskConfig.has("dtmfReleaseElevator") ? myWebSocketService.kioskConfig.optString("dtmfReleaseElevator") : "7";
                            boolean door = number.equals(dtmfOpenDoor);
                            boolean floor = number.equals(dtmfReleaseElevator);

                            if ((door || floor) && (!isFloorOpened && !(isDoorOpened && !floor))) {
                                if(!isDoorOpened) {
                                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                        Log.e("tag", ":::::STOPPING SERVICE:::::");
                                        detachJanusPlugin();
                                    }, 5000);
                                    isDoorOpened = true;
                                }
                                if (floor) isFloorOpened = true;
                                myWebSocketService.handleDtmfSignal(floor);
                            }
                        }
//                        else if (plugin.optString("event").equals("accepted")) {
//                         Not sure if we need this for now.
//                        }

                        JSONObject jsep = jo.optJSONObject("jsep");
                        if (jsep != null) {
                            myJsep = jsep;
                            handle.onRemoteJsep.onRemoteJsep(handle, jsep);
                            myWebSocketService.callSetRemoteDescriptionForCall();
                        }

                    } else if (janus.equals("detached")) {
                        handle.onLeaving.onJoined(handle);
                    }
                    myHandle = handle;
                    break;
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void createIncomingCallNotification() {
        isIncomingCall = true;
        myWebSocketService.acceptCallWs();
    }

    private void getUserList() {
        JSONObject msg = new JSONObject();
        JSONObject body = new JSONObject();
        try {
            body.putOpt("request", "list");
            body.putOpt("token", myToken);

            msg.putOpt("janus", "message");
            msg.putOpt("token", myToken);
            msg.putOpt("body", body);
            msg.putOpt("transaction", randomString());
            msg.putOpt("session_id", mSessionId);
            msg.putOpt("handle_id", myHandle.handleId);

        } catch (JSONException e) {
            e.printStackTrace();
        }
        mJanusWebSocket.send(msg.toString());
    }

    public void callUser(SessionDescription sdp) {
        JSONObject msg = new JSONObject();
        JSONObject body = new JSONObject();
        JSONObject jsep = new JSONObject();
        try {
            body.putOpt("request", "call");
            if (myPlugin.contains("videocall")) {
                body.putOpt("username", myWebSocketService.calluser);
                body.putOpt("token", myToken);
            } else body.putOpt("uri", "sip:" + phoneNumber + "@" + sip.optString("sipServer"));

            jsep.putOpt("type", "offer");
            jsep.putOpt("sdp", sdp.description);

            msg.putOpt("janus", "message");
            msg.putOpt("token", myToken);
            msg.putOpt("body", body);
            msg.putOpt("transaction", randomString());
            msg.putOpt("session_id", mSessionId);
            msg.putOpt("handle_id", myHandle.handleId);
            msg.putOpt("jsep", jsep);

        } catch (JSONException e) {
            e.printStackTrace();
        }
        mJanusWebSocket.send(msg.toString());
        myWebSocketService.calluser = "";
    }

    public void acceptCall(SessionDescription sdp) {
        JSONObject msg = new JSONObject();
        JSONObject body = new JSONObject();
        JSONObject jsep = new JSONObject();
        try {
            body.putOpt("request", "accept");
            body.putOpt("token", myToken);

            jsep.putOpt("type", "answer");
            jsep.putOpt("sdp", sdp.description);

            msg.putOpt("janus", "message");
            msg.putOpt("token", myToken);
            msg.putOpt("body", body);
            msg.putOpt("transaction", randomString());
            msg.putOpt("session_id", mSessionId);
            msg.putOpt("handle_id", myHandle.handleId);
            msg.putOpt("jsep", jsep);

        } catch (JSONException e) {
            e.printStackTrace();
        }
        mJanusWebSocket.send(msg.toString());
    }

    public void detachJanusPlugin() {
        this.mJanusWebSocket.close(1000, "");
        stopSelf();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (myWebSocketService != null)
            myWebSocketService.handlehangup();
        myJsep = null;
        WebSocketService.SingletonServiceManager.isMyServiceRunning = false;
    }

    public class MyLocalBinder extends Binder {
        public com.example.smartpropertykiosk.JanusConnectionService getService() {
            return com.example.smartpropertykiosk.JanusConnectionService.this;
        }
    }

    private final Runnable fireKeepAlive = new Runnable() {
        @Override
        public void run() {
            keepAlive();
            mHandler.postDelayed(fireKeepAlive, 30000);
        }
    };


}