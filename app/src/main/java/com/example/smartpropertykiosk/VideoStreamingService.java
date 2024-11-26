package com.example.smartpropertykiosk;

import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;

import org.jetbrains.annotations.NotNull;
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

public class VideoStreamingService extends Service {


    private static final String TAG = ":::VideoStreaming:::";
    private final ConcurrentHashMap<String, JanusTransaction> transactions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<BigInteger, JanusHandle> handles = new ConcurrentHashMap<>();

    @Nullable
    private final IBinder myBinder = new MyLocalBinder();
    public JSONObject myJsep;
    public String myRoom;
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
    private WebSocket mJanusWebSocket;
    private String myPlugin;
    private String myServer;
    private String myPort;
    private String myUsername;
    private String myToken;
    private JanusHandle myHandle;

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
        myRoom = intent.getStringExtra("room");
        Log.e("tag", myRoom);
        startButtonClicked();

        return START_NOT_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();

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
                })
                .connectTimeout(10, TimeUnit.SECONDS)
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
//                WebSocketChannel.this.onMessage(text);
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
//                restartActivity();
            }

            @Override
            public void onFailure(@NotNull WebSocket webSocket, @NotNull Throwable t, Response response) {
                Log.e(TAG, "onFailure" + t);
                onDestroy();
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

        try {
            body.putOpt("request", "join");
            body.putOpt("room", Long.parseLong(myRoom));
            body.putOpt("ptype", "publisher");
            body.putOpt("display", myUsername);
            body.putOpt("token", myToken);

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
                    String detail = jo.optJSONObject("error").optString("reason");
                    new Handler(Looper.getMainLooper()).postDelayed(()->{
                        myWebSocketService.setVideoFaultCode("set", "SECURITYVIDEO", detail);
                    },10);
                    if (!detail.contains("Unauthorized request")) {
                        new Handler(Looper.getMainLooper()).postDelayed(this::restartActivity, 10000);
                        detachJanusPlugin();
                    }
                    break;
                }
                case "media":
                    if(!jo.optBoolean("receiving")) {
                        System.exit(9);
                    }
                    break;
                case "ack":
                    Log.e(TAG, "Just an ack");
                    break;
                case "webrtcup":
                    break;
                default:
                    JanusHandle handle = handles.get(new BigInteger(jo.optString("sender")));
                    if (handle == null) {
                        Log.e(TAG, "missing handle");
                    } else if (janus.equals("event")) {

                        if (jo.optJSONObject("plugindata").optJSONObject("data").optString("videoroom").equals("joined")) {
                            handle.onJoined.onJoined(handle);
                            new Handler(Looper.getMainLooper()).postDelayed(() -> myWebSocketService.callCreateOfferForVS(), 10);
                        }

                        JSONObject jsep = jo.optJSONObject("jsep");
                        if (jsep != null) {
                            myJsep = jsep;
                            handle.onRemoteJsep.onRemoteJsep(handle, jsep);
                            myWebSocketService.callSetRemoteDescriptionForVS();
                        }

                    } else if (janus.equals("detached")) {
                        handle.onLeaving.onJoined(handle);
                    }
                    myHandle = handle;
                    Log.e(TAG, myHandle.handleId.toString());
                    break;
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void publisherCreateOffer(final SessionDescription sdp) {
        JSONObject publish = new JSONObject();
        JSONObject jsep = new JSONObject();
        JSONObject message = new JSONObject();
        try {
            publish.putOpt("request", "configure");
            publish.putOpt("audio", false);
            publish.putOpt("video", true);

            jsep.putOpt("type", sdp.type);
            jsep.putOpt("sdp", sdp.description);

            message.putOpt("janus", "message");
            message.putOpt("body", publish);
            message.putOpt("jsep", jsep);
            message.putOpt("token", myToken);
            message.putOpt("transaction", randomString());
            message.putOpt("session_id", mSessionId);
            message.putOpt("handle_id", myHandle.handleId);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        mJanusWebSocket.send(message.toString());
        myWebSocketService.setVideoFaultCode("clear", "SECURITYVIDEO", "");
    }

    public void detachJanusPlugin() {
        this.mJanusWebSocket.close(1000, "");
        stopSelf();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        this.detachJanusPlugin();
        new Handler(Looper.getMainLooper()).postDelayed(this::restartActivity, 0);
    }

    private void restartActivity() {
        System.exit(8);
    }

    public class MyLocalBinder extends Binder {
        public com.example.smartpropertykiosk.VideoStreamingService getService() {
            return com.example.smartpropertykiosk.VideoStreamingService.this;
        }
    }

    private final Runnable fireKeepAlive = new Runnable() {
        @Override
        public void run() {
            mHandler.postDelayed(fireKeepAlive, 30000);
            keepAlive();
        }
    };


}
