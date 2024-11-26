package com.example.smartpropertykiosk;

import static android.view.View.KEEP_SCREEN_ON;
import static android.view.View.SYSTEM_UI_FLAG_FULLSCREEN;
import static android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
import static android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
import static android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
import static android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okhttp3.logging.HttpLoggingInterceptor;
import okio.ByteString;

public class StartupActivity extends AppCompatActivity {
    SharedPreferences sp;
    LinearLayout noServerLL;
    TextView noServerErrorTextView;
    Button refreshBtn;

    String TAG = ":::WEBSOCKET-TEST:::";
    private WebSocket mWebSocket1;
    private ArrayList<String> groupArray = null;

    public static void triggerRebirth() {
        System.exit(6);
    }

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//        startLockTask();
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE
                | SYSTEM_UI_FLAG_FULLSCREEN
                | SYSTEM_UI_FLAG_LAYOUT_STABLE
                | SYSTEM_UI_FLAG_IMMERSIVE_STICKY | KEEP_SCREEN_ON
                | SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | SYSTEM_UI_FLAG_HIDE_NAVIGATION);

        Log.e("StartupActivity", "onCreate called.");

        setContentView(R.layout.startup_activity);
        sp = getSharedPreferences("pcplus", MODE_PRIVATE);

        noServerLL = findViewById(R.id.no_server_ll);
        noServerErrorTextView = findViewById(R.id.no_server_error);
        refreshBtn = findViewById(R.id.refreshBtn);

        Set<String> set = sp.getStringSet("groupArray", null);
        groupArray = new ArrayList<>(set);

        if (groupArray.size() > 0) {
            findViewById(R.id.loading_ll).setVisibility(View.VISIBLE);
            if (sp.getAll().containsKey("SERVICEGROUP") && !sp.getString("SERVICEGROUP", "").equals("")) {
                getApiCredentials(sp.getString("SERVICEGROUP", ""));
            } else if (groupArray.size() == 1) {
                sp.edit().putString("groupName", groupArray.get(0)).apply();
                getApiCredentials(groupArray.get(0));
            } else {
                createRadioButton();
            }
        } else {
            noServerErrorTextView.setText("No Server found on the network. Please check the network connection and try again.");
            noServerLL.setVisibility(View.VISIBLE);
            //new Handler(Looper.getMainLooper()).postDelayed(StartupActivity::triggerRebirth, 30000);
        }

        refreshBtn.setOnClickListener(v -> onClickRefreshBtn());
        findViewById(R.id.refreshBtn1).setOnClickListener(v -> onClickRefreshBtn());
        findViewById(R.id.refreshBtn2).setOnClickListener(v -> onClickRefreshBtn());
    }

    @Override
    public void onBackPressed() {
        // Don't want user to click back button to kill app and make changes to our device
    }

    private void onClickRefreshBtn() {
        sp.edit().putString("SERVICEGROUP", "").commit();
        sp.edit().putString("ELPNAME", "").commit();
        //new Handler(Looper.getMainLooper()).postDelayed(StartupActivity::triggerRebirth, 100);
    }

    private void startSession(String elp) {
        JSONObject msg = new JSONObject();
        try {
            msg.putOpt("command", "start");
            if (!elp.equals(""))
                msg.putOpt("select", elp);
            msg.putOpt("tag", "startSession");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        Log.v("Open socket:", msg.toString());
        mWebSocket1.send(msg.toString());
    }

    public static OkHttpClient.Builder getUnsafeOkHttpClientBuilder() {
        try {
            // Create a trust manager that does not validate certificate chains
            final TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                        }

                        @Override
                        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                        }

                        @Override
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[]{};
                        }
                    }
            };

            // Install the all-trusting trust manager
            final SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

            OkHttpClient.Builder builder = new OkHttpClient.Builder();
            builder.sslSocketFactory(sslSocketFactory, (X509TrustManager) trustAllCerts[0]);
            builder.hostnameVerifier((hostname, session) -> true);

            return builder;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void getApiCredentials(String groupName) {
        OkHttpClient httpClient = getUnsafeOkHttpClientBuilder()
                .addNetworkInterceptor(new HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY))
                .addInterceptor(chain -> {
                    Request.Builder builder = chain.request().newBuilder();
                    builder.addHeader("Sec-WebSocket-Protocol", "janus-protocol");
                    return chain.proceed(builder.build());
                }).connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build();
//        String url = "wss://elp." + groupName + ".condoplex.net:38500";
        String url = "wss://condoplexinc.com:38500";
        Request request = new Request.Builder().url(url).build();
        mWebSocket1 = httpClient.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(@NotNull WebSocket webSocket, @NotNull Response response) {
                Log.e(TAG, "onOpen");
                Log.e(TAG, response.toString());
                startSession("");
            }

            @Override
            public void onMessage(@NotNull WebSocket webSocket, @NotNull String text) {
                Log.e(TAG, "onMessage");
                Log.e(TAG, text);
                try {
                    JSONObject objText = new JSONObject(text);
                    if (objText.has("result") && objText.get("result").toString().equals("success")) {
                        if (objText.has("ELPLIST")) {
                            JSONArray elpList = new JSONArray(objText.getString("ELPLIST"));
                            runOnUiThread(() -> {
                                try {
                                    createElpListRadioBtn(elpList);
                                } catch (JSONException e) {
                                    e.printStackTrace();
                                }
                            });
                        } else if (objText.has("SERVICEPASS") && !objText.optString("ELPNAME").equals("DISPLAYMANAGER")) {
                            sp.edit().putString("SERVICEPASS", objText.getString("SERVICEPASS")).apply();
                            sp.edit().putString("SERVICENAME", objText.getString("SERVICENAME")).apply();
                            sp.edit().putString("SERVICEGROUP", objText.getString("SERVICEGROUP")).apply();
                            sp.edit().putString("ELPNAME", objText.getString("ELPNAME")).apply();
                            sp.edit().putString("last_SG",objText.getString("SERVICEGROUP")).apply();
                            goToMainActivityNow();
                        } else {
                            sp.edit().putString("SERVICEGROUP", "").apply();
                            runOnUiThread(() -> {
                                findViewById(R.id.no_elp_error).setVisibility(View.VISIBLE);
                                findViewById(R.id.loading_ll).setVisibility(View.GONE);
                                findViewById(R.id.retry_btn).setOnClickListener(v -> triggerRebirth());
                                //new Handler(Looper.getMainLooper()).postDelayed(StartupActivity::triggerRebirth, 30000);
                            });
                        }
                    } else {
                        sp.edit().putString("SERVICEGROUP", "").apply();
                        runOnUiThread(() -> {
                            findViewById(R.id.no_elp_error).setVisibility(View.VISIBLE);
                            findViewById(R.id.loading_ll).setVisibility(View.GONE);
                            findViewById(R.id.retry_btn).setOnClickListener(v -> triggerRebirth());
                            //new Handler(Looper.getMainLooper()).postDelayed(StartupActivity::triggerRebirth, 30000);
                        });
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onMessage(@NotNull WebSocket webSocket, @NotNull ByteString bytes) {
                Log.e(TAG, "onMessage BYTES");
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
                Log.e(TAG, "onFailure-" + t);
                runOnUiThread(() -> {
                    noServerErrorTextView.setText("Can not connect to the Server: \"" + groupName + "\". \nPlease contact Condoplex team.");
                    findViewById(R.id.no_server_ll).setVisibility(View.VISIBLE);
                    findViewById(R.id.loading_ll).setVisibility(View.GONE);
                });
                //new Handler(Looper.getMainLooper()).postDelayed(StartupActivity::triggerRebirth, 30000);
            }
        });

    }

    private void goToMainActivityNow() {
        mWebSocket1.close(1001, "Closing websocket that we dont need now.");
        Log.e(TAG, "Closing websocket that we dont need now.");
        startActivity(new Intent(this, MainActivity.class));
    }

    private void createElpListRadioBtn(JSONArray elpList) throws JSONException {

        LinearLayout ll = findViewById(R.id.kiosk_list_ll);

        final RadioButton[] rb = new RadioButton[elpList.length()];
        RadioGroup rg = new RadioGroup(this); //create the RadioGroup
        rg.setOrientation(RadioGroup.VERTICAL);//or RadioGroup.VERTICAL

        for (int i = 0; i < elpList.length(); i++) {
            rb[i] = new RadioButton(this);
            rb[i].setText(elpList.getJSONObject(i).getString("name"));
            rb[i].setTextSize(32);
            rb[i].setId(i + 200);
            rb[i].setButtonTintList(ColorStateList.valueOf(Color.parseColor("#5E7DBE")));
            rb[i].setPadding(10, 10, 10, 10);
            rg.addView(rb[i]);
        }

        ((LinearLayout) findViewById(R.id.radio_list_ll2)).addView(rg);
        ll.setVisibility(View.VISIBLE);
        findViewById(R.id.loading_ll).setVisibility(View.GONE);

        rg.setOnCheckedChangeListener((group, checkedId) -> findViewById(R.id.submit_btn2).setEnabled(true));

        //starting timmer to auto select previously selected site
        Handler mHandler = new Handler();
        Runnable mRunnable = () -> {
            ll.setVisibility(View.GONE);
            findViewById(R.id.submit_btn2).setEnabled(false);
            findViewById(R.id.loading_ll).setVisibility(View.VISIBLE);
            startSession(sp.getString("ELPNAME", ""));

        };
        if (!sp.getString("ELPNAME", "").equals("")) {
            //mHandler.postDelayed(mRunnable, 15000);
        }

        findViewById(R.id.submit_btn2).setOnClickListener(v -> {
            mHandler.removeCallbacks(mRunnable);
            ll.setVisibility(View.GONE);
            findViewById(R.id.submit_btn2).setEnabled(false);
            findViewById(R.id.loading_ll).setVisibility(View.VISIBLE);
            String kioskName = ((RadioButton) findViewById(rg.getCheckedRadioButtonId())).getText().toString();
            startSession(kioskName);
        });
    }

    private void createRadioButton() {

        LinearLayout ll = findViewById(R.id.ll);

        final RadioButton[] rb = new RadioButton[groupArray.size()];
        RadioGroup rg = new RadioGroup(this); //create the RadioGroup
        rg.setOrientation(RadioGroup.VERTICAL);//or RadioGroup.VERTICAL
        for (int i = 0; i < groupArray.size(); i++) {
            rb[i] = new RadioButton(this);
            rb[i].setText(groupArray.get(i));
            rb[i].setTextSize(32);
            rb[i].setId(i + 100);
            rb[i].setButtonTintList(ColorStateList.valueOf(Color.parseColor("#5E7DBE")));
            rb[i].setPadding(10, 10, 10, 10);
            rg.addView(rb[i]);
        }

        ((LinearLayout) findViewById(R.id.radio_list_ll)).addView(rg);
        ll.setVisibility(View.VISIBLE);
        findViewById(R.id.loading_ll).setVisibility(View.GONE);

        rg.setOnCheckedChangeListener((group, checkedId) -> findViewById(R.id.submit_btn).setEnabled(true));

        findViewById(R.id.submit_btn).setOnClickListener(v -> {
            handler1.removeCallbacks(runMe);
            ll.setVisibility(View.GONE);
            findViewById(R.id.submit_btn).setEnabled(false);
            findViewById(R.id.loading_ll).setVisibility(View.VISIBLE);
            String gn = ((RadioButton) findViewById(rg.getCheckedRadioButtonId())).getText().toString();
            getApiCredentials(gn);
        });

        //handler1.postDelayed(runMe,20000);
    }

    private final Handler handler1 = new Handler(Looper.getMainLooper());
    private final Runnable runMe = ()->{
        String x = sp.getString("last_SG", "AAAAAA");
        Log.e("StartupActivity", "Will check now... " + x);
        if (!x.isEmpty() && groupArray != null && groupArray.contains(x)) {
            Log.e("StartupActivity", "Connecting to " + x);
            runOnUiThread(()->{
                findViewById(R.id.ll).setVisibility(View.GONE);
                findViewById(R.id.submit_btn).setEnabled(false);
                findViewById(R.id.loading_ll).setVisibility(View.VISIBLE);
            });
            getApiCredentials(x);
        } else {
            Log.e("StartupActivity", "Will reset now");
            triggerRebirth();
        }
    };

}
