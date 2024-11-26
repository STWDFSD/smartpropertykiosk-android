package com.example.smartpropertykiosk;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.EditText;
import android.widget.ExpandableListView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.slider.Slider;
import com.smarteist.autoimageslider.IndicatorView.animation.type.IndicatorAnimationType;
import com.smarteist.autoimageslider.SliderAnimations;
import com.smarteist.autoimageslider.SliderView;
import com.squareup.picasso.Picasso;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.EglRenderer;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class MainActivity extends AppCompatActivity implements WebSocketCallBackInterface {

    public static Handler residentResetHandler = new Handler();
    public static Handler clearSearchBarHandler = new Handler();
    public static Handler dismissVolumePopupHandler = new Handler();

    static {
        System.loadLibrary("iotest");
    }

    private final List<PeerConnection.IceServer> peerIceServers = new ArrayList<>();
    private final Handler handler2 = new Handler();
    public boolean isSendingSecurityVideo = false;
    public VideoCapturer videoCapturerAndroid;
    public Runnable myRunnable = null;
    public Runnable clearSearchBar = null;
    public Runnable toDismissVolumePopup = null;
    SharedPreferences sp;
    AudioManager audioManager;
    EditText searchView;
    WebSocketService myWebSocketService;
    VideoStreamingService myVSservice;
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
            myWebSocketService.setCallbacks(MainActivity.this);
        }
    };
    boolean isBoundvs = false;
    private final ServiceConnection serviceConnectorVS = new ServiceConnection() {
        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBoundvs = false;
        }

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            VideoStreamingService.MyLocalBinder binder = (VideoStreamingService.MyLocalBinder) service;
            myVSservice = binder.getService();
            isBoundvs = true;
//            myWebSocketService.setCallbacks(MainActivity.this);
        }
    };
    TextView siteName;
    TextView addressLine;
    ExpandableListView usersList;
    ArrayList<JSONObject> listGroup = new ArrayList<>();
    UserListAdapter adapter;
    GuardListAdapter guardListAdapter;
    ListView guardList;
    ArrayList<JSONObject> guardListGroup = new ArrayList<>();
    CardView userListView;
    CardView pincodeView;
    CardView dialcodeView;
    MaterialButtonToggleGroup materialButtonToggleGroup;
    EditText pinCodeEditText;
    String tempPinCode;
    Button getAccessBtn;
    TextView pinResultText;
    TextView pinResultSubText;
    EditText dialCodeEditText;
    TextView dialCodeResultText;
    TextView dialCodeResultSubText;
    Button volumeBtn;
    PopupWindow popUp;
    JanusConnectionService myService;
    TextView kioskName;
    ImageView imgss;
    TextView text_ss;
    private final Runnable r2 = () -> {
        //show screen saver here!!
        imgss.setVisibility(View.VISIBLE);
        text_ss.setVisibility(View.VISIBLE);
        //TODO - change back to default language
        if (myWebSocketService.kioskConfig.optJSONObject("displayConfiguration") != null) {
            setLocale(Objects.requireNonNull(myWebSocketService.kioskConfig.optJSONObject("displayConfiguration")).optString("defaultLanguage", "en"));
        }
    };
    Boolean isIntercomEnabled = false;
    boolean isBoundJanus = false;
    //service connector for janusConnectionService
    private final ServiceConnection serviceConnectorJanus = new ServiceConnection() {
        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBoundJanus = false;
        }

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            JanusConnectionService.MyLocalBinder binder = (JanusConnectionService.MyLocalBinder) service;
            myService = binder.getService();
            isBoundJanus = true;
        }
    };
    boolean isScreenSaver = true;
    //For multiple click event
    int isDoubleClicked = 0;
    Handler handler1 = new Handler();
    Runnable r = () -> isDoubleClicked = 0;
    private int ssTimeout = 0;
    private View overlay;
    private VideoTrack localVideoTrack;
    private AudioTrack localAudioTrack;
    private VideoSource videoSource;
    private SurfaceViewRenderer localVideoView;
    private SurfaceViewRenderer remoteVideoView;
    private EglBase rootEglBase;
    private PeerConnectionFactory peerConnectionFactory1;
    private PeerConnection localPeer1;
    private PeerConnection localPeer;
    private CountDownTimer countDownTimer;
    private boolean KeyboardOpened = false;

    public MainActivity() {
    }

    public static native void output(int tag);

    public static native int input(int tag);

    public static JSONObject getJSON(String city, String country) {
        String OPEN_WEATHER_MAP_API =
                "https://api.openweathermap.org/data/2.5/weather?q=%s&units=";
        OPEN_WEATHER_MAP_API = OPEN_WEATHER_MAP_API + (country.equals("Canada") ? "metric" : "imperial");
        try {
            URL url = new URL(String.format(OPEN_WEATHER_MAP_API, city + "," + country));
            HttpURLConnection connection =
                    (HttpURLConnection) url.openConnection();

            connection.addRequestProperty("x-api-key",
                    "4e8877956d2e3eba7d14125f08f3cf15");

            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));

            StringBuilder json = new StringBuilder(1024);
            String tmp;
            while ((tmp = reader.readLine()) != null)
                json.append(tmp).append("\n");
            reader.close();

            JSONObject data = new JSONObject(json.toString());

            if (data.getInt("cod") != 200) {
                return null;
            }
            return data;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Function to convert string to title case
     *
     * @param string - Passed string
     */
    public static String toTitleCase(String string) {
        // Check if String is null
        if (string == null) {
            return null;
        }
        boolean whiteSpace = true;
        StringBuilder builder = new StringBuilder(string); // String builder to store string
        final int builderLength = builder.length();

        // Loop through builder
        for (int i = 0; i < builderLength; ++i) {
            char c = builder.charAt(i); // Get character at builders position
            if (whiteSpace) {
                // Check if character is not white space
                if (!Character.isWhitespace(c)) {
                    // Convert to title case and leave whitespace mode.
                    builder.setCharAt(i, Character.toTitleCase(c));
                    whiteSpace = false;
                }
            } else if (Character.isWhitespace(c)) {
                whiteSpace = true; // Set character is white space
            } else {
                builder.setCharAt(i, Character.toLowerCase(c)); // Set character to lowercase
            }
        }
        return builder.toString(); // Return builders text
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    @SuppressLint({"ClickableViewAccessibility", "SetTextI18n", "MissingInflatedId"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {

//        setLocale("fr");

        Log.e("MainActivity", "onCreate called.");

        sp = getSharedPreferences("pcplus", MODE_PRIVATE);
        super.onCreate(savedInstanceState);
//        startLockTask();
        new Thread(new TcpServer()).start();

        //global exception handler
        Thread.setDefaultUncaughtExceptionHandler((thread, e) -> {
            Log.e("Global Exception", e.toString());
            new PingAndLogHelper(getApplicationContext(), "log",
                    sp.getString("SERVICEGROUP", ""),
                    sp.getString("ELPNAME", ""),
                    BuildConfig.VERSION_NAME,
                    "GlobalException",
                    e.toString().substring(0, 100));
            if (e instanceof OutOfMemoryError || e instanceof SecurityException) {
                try (DataOutputStream os = new DataOutputStream(Runtime.getRuntime().exec("su").getOutputStream())) {
                    os.writeBytes("reboot\n");
                    os.flush();
                } catch (IOException ex) {
                    ex.printStackTrace();
                    restart();
                }
            } else {
                restart();
            }
        });

        input(1);
        input(2);
        output(4);
        output(6);

        Intent i = new Intent(this, WebSocketService.class);
        startService(i);
        isBound = getApplicationContext().bindService(i, serviceConnector, 0);

        //to get full screen activity
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }
//        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.KEEP_SCREEN_ON
//                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
//                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
//                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
//                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
//                        | View.SYSTEM_UI_FLAG_FULLSCREEN);

        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.KEEP_SCREEN_ON
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN);
        getWindow().getDecorView().setOnSystemUiVisibilityChangeListener(
                visibility -> {
                    if ((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
                        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.KEEP_SCREEN_ON
                                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_FULLSCREEN);
                    }
                });

        popUp = new PopupWindow(this);

        setContentView(R.layout.activity_main);

//        TextView textView = findViewById(R.id.welcomeText);
//        textView.setText(R.string.welcome_to);

        audioManager = (AudioManager) getApplicationContext().getSystemService(Context.AUDIO_SERVICE);

        kioskName = findViewById(R.id.kioskName);
        siteName = findViewById(R.id.siteName);
        addressLine = findViewById(R.id.addressText);
        usersList = findViewById(R.id.user_list);
        guardList = findViewById(R.id.guard_list);
        searchView = findViewById(R.id.searchUserTextField);
        userListView = findViewById(R.id.user_list_cardview);
        pincodeView = findViewById(R.id.pincode_cardview);
        dialcodeView = findViewById(R.id.dialcode_cardview);
        materialButtonToggleGroup = findViewById(R.id.toggle_button_group);
        pinCodeEditText = findViewById(R.id.pinCodeEditText);
        dialCodeEditText = findViewById(R.id.dialCodeEditText);
        getAccessBtn = findViewById(R.id.get_access_btn);
        pinResultText = findViewById(R.id.pinResultText);
        pinResultSubText = findViewById(R.id.pinResultSubText);
        dialCodeResultText = findViewById(R.id.dialCodeResultText);
        dialCodeResultSubText = findViewById(R.id.dialCodeResultSubText);
        volumeBtn = findViewById(R.id.volume_btn);
        myRunnable = () -> materialButtonToggleGroup.check(R.id.guest_btn);
        clearSearchBar = () -> {
            searchView.clearFocus();
            hideKeyboard(searchView);
            searchView.setText("");
        };
        rootEglBase = EglBase.create();
        overlay = findViewById(R.id.overlay);
        Button menubtn1 = findViewById(R.id.menubtn1);
        text_ss = findViewById(R.id.text_ss);
        imgss = findViewById(R.id.imgss);
        if (isIntercomEnabled) {
            findViewById(R.id.user_list_cardview).setVisibility(View.GONE);
            findViewById(R.id.toggle_button_group).setVisibility(View.GONE);

            LinearLayout linearLayout = findViewById(R.id.welcome_text);
            linearLayout.setGravity(Gravity.CENTER);

            menubtn1.setVisibility(View.GONE);

        } else {
            ConstraintLayout corosl = findViewById(R.id.corosl);
            corosl.setVisibility(View.GONE);
            findViewById(R.id.callingButtons).setVisibility(View.GONE);

        }
        imgss.setOnClickListener(v -> {
            imgss.setVisibility(View.GONE);
            text_ss.setVisibility(View.GONE);
        });

        menubtn1.setOnClickListener(v -> {

            guardListGroup.clear();

            for (int x = 0; x < myWebSocketService.kioskConfig.optJSONObject("displayConfiguration").optJSONArray("buttons").length(); x++) {
                guardListGroup.add(myWebSocketService.kioskConfig.optJSONObject("displayConfiguration").optJSONArray("buttons").optJSONObject(x));
            }

            guardListAdapter = new GuardListAdapter(guardListGroup, this);

            LayoutInflater inflater = (LayoutInflater)
                    getSystemService(LAYOUT_INFLATER_SERVICE);
            @SuppressLint("InflateParams") View popupView = inflater.inflate(R.layout.guard_menu_popup, null);

            int width = 500;
            int height = LinearLayout.LayoutParams.WRAP_CONTENT;
            boolean focusable = true;

            final PopupWindow popupWindow = new PopupWindow(popupView, width, height, focusable);

            popupWindow.showAtLocation(v, Gravity.CENTER, 0, 0);
            overlay.setVisibility(View.VISIBLE);
            isScreenSaver = false;
            handler2.removeCallbacks(r2);
            ((ListView) popupView.findViewById(R.id.guard_list)).setAdapter(guardListAdapter);

            popupWindow.setOnDismissListener(() -> {
                overlay.performClick();
                if (ssTimeout > 0) {
                    handler2.postDelayed(r2, ssTimeout * 1000);
                    isScreenSaver = true;
                }

            });

            ((ListView) popupView.findViewById(R.id.guard_list)).setOnItemClickListener((AdapterView<?> parent, View view, int position, long id) -> {
                popupWindow.dismiss();
                JSONObject buttonData = (JSONObject) parent.getItemAtPosition(position);
                if (buttonData.has("phoneNumber") && buttonData.optString("phoneNumber").length() == 10) {
                    myWebSocketService.tryCallingUser(buttonData, "");
                } else {
                    myWebSocketService.callGuard(45000, buttonData);

                }
                isScreenSaver = false;
                handler2.removeCallbacks(r2);
                findViewById(R.id.user_list_cardview).setVisibility(View.GONE);
                findViewById(R.id.calling_screen_card).setVisibility(View.VISIBLE);

                ((TextView) findViewById(R.id.username_text)).setText("Guard");
//            findViewById(R.id.end_call).setVisibility(View.GONE);
//            ((TextView) findViewById(R.id.call_status_text)).setText("Incoming Call...");
            });
        });

        FloatingActionButton redBtn = findViewById(R.id.end_call);
        redBtn.setOnClickListener(v -> {
            if (myService != null) {
                myService.detachJanusPlugin();
            } else hangup();
        });

        pinCodeEditText.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                pinResultText.setText("");
                pinResultSubText.setText("");
                if (myRunnable != null)
                    residentResetHandler.removeCallbacks(myRunnable);
//                residentResetHandler.postDelayed(myRunnable, 10000);
            }
            shouldRestrictUser();
        });

        dialCodeEditText.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                dialCodeResultText.setText("");
                dialCodeResultSubText.setText("");
                if (myRunnable != null)
                    residentResetHandler.removeCallbacks(myRunnable);
//                residentResetHandler.postDelayed(myRunnable, 10000);
            }
        });

        pinCodeEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() > 0 && shouldRestrictUser()) {
                    pinCodeEditText.setText("");
                    pinCodeEditText.clearFocus();
                    hideKeyboard(pinCodeEditText);
                } else getAccessBtn.setEnabled(s.length() > 0);
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (myRunnable != null)
                    residentResetHandler.removeCallbacks(myRunnable);
//                residentResetHandler.postDelayed(myRunnable, 10000);
            }
        });

        dialCodeEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                dialCodeResultText.setText("");
                dialCodeResultSubText.setText("");
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String code = s.toString();
                //check for pin code
                if (code.startsWith("*") && shouldRestrictUser()) {
                    dialCodeEditText.clearFocus();
                    hideKeyboard(dialCodeEditText);
                } else if (code.startsWith("*") && code.length() > 4) {
                    tempPinCode = code.substring(1);
                    int pin = Integer.parseInt(tempPinCode);
                    if (pin == myWebSocketService.kioskConfig.optInt("technicianCode", 0)) {
                        stopLockTask();
                        Intent techIntent = new Intent(getApplicationContext(), TechnicianActivity.class);
                        startActivity(techIntent);
                    } else {
                        myWebSocketService.getPinCodeAccess(pin);
                    }
                    dialCodeEditText.setText("");
                }

                //check for user and call if found. if not found show error
                else if (!code.startsWith("*") && code.length() >= 3)
                    checkForDialCode(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (myRunnable != null)
                    residentResetHandler.removeCallbacks(myRunnable);
//                residentResetHandler.postDelayed(myRunnable, 10000);
            }
        });

        getAccessBtn.setOnClickListener(v -> {
            tempPinCode = String.valueOf(pinCodeEditText.getText());
            myWebSocketService.getPinCodeAccess(Integer.parseInt(tempPinCode));
            getAccessBtn.setEnabled(false);
            pinCodeEditText.setText("");

            if (myRunnable != null)
                residentResetHandler.removeCallbacks(myRunnable);
//            residentResetHandler.postDelayed(myRunnable, 10000);
        });


        materialButtonToggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            clearSearchBarHandler.removeCallbacks(clearSearchBar);
//            clearSearchBarHandler.postDelayed(clearSearchBar, 0);
            pinCodeEditText.setText("");
            pinResultText.setText("");
            pinResultSubText.setText("");
            dialCodeEditText.setText("");
            dialCodeResultText.setText("");
            dialCodeResultSubText.setText("");
            if (R.id.guest_btn == checkedId) {
                //guest is checked
                pincodeView.setVisibility(View.GONE);
                dialcodeView.setVisibility(View.GONE);
                userListView.setVisibility(View.VISIBLE);
                residentResetHandler.removeCallbacks(myRunnable);
            } else if (R.id.resident_btn == checkedId) {
                //resident is checked
                userListView.setVisibility(View.GONE);
                dialcodeView.setVisibility(View.GONE);
                pincodeView.setVisibility(View.VISIBLE);
//                residentResetHandler.postDelayed(myRunnable, 10000);
                shouldRestrictUser();
            } else {
                //dialCode is checked
                userListView.setVisibility(View.GONE);
                dialcodeView.setVisibility(View.VISIBLE);
                pincodeView.setVisibility(View.GONE);
//                residentResetHandler.postDelayed(myRunnable, 10000);
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    dialCodeEditText.requestFocus();
                    showKeyboard(dialCodeEditText);
                }, 1);
            }
        });

        usersList.setOnGroupExpandListener(new ExpandableListView.OnGroupExpandListener() {
            int previousGroup = -1;

            @Override
            public void onGroupExpand(int groupPosition) {
                clearSearchBarHandler.removeCallbacks(clearSearchBar);
//                clearSearchBarHandler.postDelayed(clearSearchBar, 10000);
                if (groupPosition != previousGroup) {
                    usersList.collapseGroup(previousGroup);
                    previousGroup = groupPosition;
                }
                //postDelay is just for getting childview ready before setting onclick listener
                if (adapter.callBtn != null) {
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        adapter.callBtn.setOnClickListener(v -> {
                            isScreenSaver = false;
                            handler2.removeCallbacks(r2);
                            try {
                                if (!isSendingSecurityVideo && videoCapturerAndroid != null) {
                                    videoCapturerAndroid.startCapture(240, 320, 5);
                                }
                                JSONObject objText = new JSONObject(adapter.getGroup(groupPosition).toString());
                                if(localVideoTrack != null) {
                                    localVideoTrack.addSink(localVideoView);



                                    findViewById(R.id.user_list_cardview).setVisibility(View.GONE);
                                    findViewById(R.id.calling_screen_card).setVisibility(View.VISIBLE);

                                    localVideoView.addFrameListener(new EglRenderer.FrameListener() {
                                        @Override
                                        public void onFrame(Bitmap bitmap) {
                                            runOnUiThread(() -> {
                                                myWebSocketService.tryCallingUser(objText, getBase64Image(bitmap));
                                                localVideoView.removeFrameListener(this);
                                            });
                                        }
                                    }, 1);
                                } else {
                                    myWebSocketService.tryCallingUser(objText, null);
                                }

                            } catch (JSONException e) {
                                adapter.callBtn.setEnabled(true);
                                e.printStackTrace();
                            }
                        });
                    }, 10);
                } else {
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        usersList.collapseGroup(groupPosition);
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            usersList.expandGroup(groupPosition);
                        }, 1);
                    }, 1);
                }
            }
        });
        searchView.setOnTouchListener((v, event) -> {
            clearSearchBarHandler.removeCallbacks(clearSearchBar);
//            clearSearchBarHandler.postDelayed(clearSearchBar, 30000);
            if (event.getAction() == MotionEvent.ACTION_UP) {
                if (event.getRawX() >= (searchView.getRight() - searchView.getCompoundDrawables()[2].getBounds().width())) {
                    searchView.setText("");
                    searchView.clearFocus();
                    return true;
                }
            }
            return false;
        });

        searchView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (myWebSocketService == null) {
                    return;
                }
                s = s.toString().toLowerCase();
                ArrayList<JSONObject> filteredUser = new ArrayList<>();
                for (int x = 0; x < myWebSocketService.usersList.length(); x++) {
                    JSONObject y = ((JSONObject) myWebSocketService.usersList.opt(x));
                    if (y.optString("displayName").toLowerCase().contains(s) || y.optString("dialCode").contains(s)) {
                        filteredUser.add(y);
                    }
                }
                filteredUser.sort(Comparator.comparing(o -> (o.optString("displayName").toLowerCase())));

                adapter = new UserListAdapter(filteredUser);
                usersList.setAdapter(adapter);
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (!s.toString().equals("")) {
                    clearSearchBarHandler.removeCallbacks(clearSearchBar);
//                    clearSearchBarHandler.postDelayed(clearSearchBar, 30000);
                }
            }
        });

        overlay.setOnClickListener(v -> overlay.setVisibility(View.GONE));

        volumeBtn.setOnClickListener(v -> {

            // inflate the layout of the popup window
            LayoutInflater inflater = (LayoutInflater)
                    getSystemService(LAYOUT_INFLATER_SERVICE);
            @SuppressLint("InflateParams") View popupView = inflater.inflate(R.layout.volume_popup, null);

            // create the popup window

            int width = 700;
            int height = LinearLayout.LayoutParams.WRAP_CONTENT;
            boolean focusable = true; // lets taps outside the popup also dismiss it

            if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                width = 800;
            }

            final PopupWindow popupWindow = new PopupWindow(popupView, width, height, focusable);

            // show the popup window
            // which view you pass in doesn't matter, it is only used for the window tolken
            popupWindow.showAtLocation(v, Gravity.CENTER, 0, 0);
            overlay.setVisibility(View.VISIBLE);

            popupWindow.setOnDismissListener(() -> {
                if (toDismissVolumePopup != null)
                    dismissVolumePopupHandler.removeCallbacks(toDismissVolumePopup);
                overlay.performClick();
            });

            ImageButton volumeDown = popupView.findViewById(R.id.volume_down_btn);
            ImageButton volumeUp = popupView.findViewById(R.id.volume_up_btn);

            Slider volumeSlider = popupView.findViewById(R.id.volume_slider);
            volumeSlider.setValue(myWebSocketService.kioskVolume);

            volumeDown.setOnClickListener(viewBtn -> volumeSlider.setValue(0));

            volumeUp.setOnClickListener(viewBtn -> volumeSlider.setValue(100));

            volumeSlider.addOnChangeListener((slider, value, fromUser) -> {
                int vol = (int) (value * 15 / 100);
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, vol, 0);
                myWebSocketService.kioskVolume = (int) value;
            });

            toDismissVolumePopup = popupWindow::dismiss;
//            dismissVolumePopupHandler.postDelayed(toDismissVolumePopup, 10000);
        });

        findViewById(R.id.cplex_logo).setOnClickListener(v -> {
            isDoubleClicked++;
            if(BuildConfig.IS_PRODUCTION) {
                startLockTask();
            }
            handler1.removeCallbacks(r);
//            handler1.postDelayed(r, 500);
            if (isDoubleClicked == 5 && (String.valueOf(pinCodeEditText.getText()).equals("9800") || isIntercomEnabled)) {
                stopLockTask();
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    if (this.isDoubleClicked == 5) {
                        Intent techIntent = new Intent(getApplicationContext(), TechnicianActivity.class);
                        startActivity(techIntent);
                    }
                }, 450);
            }
        });


        findViewById(R.id.back_btn).setOnClickListener(v -> {
            //Go to users list
            materialButtonToggleGroup.check(R.id.guest_btn);
        });
        findViewById(R.id.back_btn1).setOnClickListener(v -> {
            //Go to users list
            materialButtonToggleGroup.check(R.id.guest_btn);
        });

        // pre configured for calls
        getReadyForVideoCalls();


        //============================Language

        LinearLayout dimOverlay = findViewById(R.id.dim_overlay);
        findViewById(R.id.language_btn).setOnClickListener(v -> {

            dimOverlay.setVisibility(View.VISIBLE);
            // Inflate the menu
            PopupMenu popupMenu = new PopupMenu(MainActivity.this, v);
            MenuInflater inflater = popupMenu.getMenuInflater();
            inflater.inflate(R.menu.language_menu, popupMenu.getMenu());

            // Handle menu item clicks
            popupMenu.setOnMenuItemClickListener(item -> {
                switch (item.getItemId()) {
                    case R.id.english:
                        // Handle English selection
                        setLocale("en");
                        return true;
                    case R.id.french:
                        // Handle Spanish selection
                        setLocale("fr");
                        return true;
                    default:
                        return false;
                }
            });
            // Show the menu
            popupMenu.show();

            popupMenu.setOnDismissListener(popupMenu1 -> dimOverlay.setVisibility(View.INVISIBLE));
        });
    }

    private void setLocale(String lang) {
        LanguageHelper.setLocale(this, lang);
        updateTexts(findViewById(R.id.root));
    }

    private void updateTexts(ViewGroup root) {
        Resources resources = getResources();
        String packageName = getPackageName();
        for (int i = 0; i < root.getChildCount(); i++) {
            View child = root.getChildAt(i);
            if (child instanceof ViewGroup) {
                updateTexts((ViewGroup) child); // Recursively update child ViewGroup
            } else if (child instanceof EditText) {
                TextView textView = (TextView) child;
                Object tag = textView.getTag();
                if (tag != null) {
                    int resId = resources.getIdentifier((String) tag, "string", packageName);
                    if (resId != 0) {
                        textView.setHint(resources.getString(resId));
                    }
                }
            } else if (child instanceof TextView) {
                TextView textView = (TextView) child;
                Object tag = textView.getTag();
                if (tag != null) {
                    int resId = resources.getIdentifier((String) tag, "string", packageName);
                    if (resId != 0) {
                        textView.setText(resources.getString(resId));
                    }
                }
            } else if (child instanceof Button) {
                Button button = (Button) child;
                Object tag = button.getTag();
                if (tag != null && tag instanceof Integer) {
                    button.setText(resources.getString((Integer) tag));
                }
            }
        }
    }

    private void startCountdownTimmer() {

        if (countDownTimer != null) {
            return;
        }

        countDownTimer = new CountDownTimer(myWebSocketService.kioskConfig.optInt("timeToRestrict", 10) * 1000L, 100) {

            @Override
            public void onTick(long l) {
                String message = "Please wait for " + ((l / 1000) + 1) + " seconds before trying again.";
                pinResultSubText.setText(message);
                pinResultText.setText("Wrong PIN entered " + sp.getInt("wrongAttempts", 0) + " times.");

                if (dialCodeEditText.getText().toString().contains("*")) {
                    dialCodeResultSubText.setText(message);
                    dialCodeResultText.setText("Wrong PIN entered " + sp.getInt("wrongAttempts", 0) + " times.");
                }
            }

            @Override
            public void onFinish() {
                pinResultText.setText("");
                pinResultSubText.setText("");
                dialCodeResultText.setText("");
                dialCodeResultSubText.setText("");
                dialCodeEditText.setText("");
                countDownTimer.cancel();
                countDownTimer = null;
            }
        };

        countDownTimer.start();
    }

    @SuppressLint("SetTextI18n")
    private boolean shouldRestrictUser() {
        if (myWebSocketService.kioskConfig.optBoolean("shouldRestrictUser", false)) {
            int wrongAttempts = sp.getInt("wrongAttempts", 0);
            int numberOfWrongAttempts = myWebSocketService.kioskConfig.optInt("numberOfWrongAttempts", 5);
            int timeToRestrict = myWebSocketService.kioskConfig.optInt("timeToRestrict", 10) * 1000;
            long lastWrongAttemptTime = sp.getLong("lastWrongAttemptTime", 0);

            if (wrongAttempts >= numberOfWrongAttempts && (System.currentTimeMillis() - lastWrongAttemptTime) < timeToRestrict) {
                pinResultText.setTextColor(getColor(R.color.cplex_error));
                pinResultSubText.setTextColor(getColor(R.color.cplex_error));
                dialCodeResultText.setTextColor(getColor(R.color.cplex_error));
                dialCodeResultSubText.setTextColor(getColor(R.color.cplex_error));
                startCountdownTimmer();
                return true;
            } else {
                pinResultText.setText("");
                pinResultSubText.setText("");
                dialCodeResultText.setText("");
                dialCodeResultSubText.setText("");
                return false;
            }
        } else {
            return false;
        }
    }

    private void checkForDialCode(String dialCode) {
        Log.e("DIAL CODE", dialCode);
        JSONArray filteredArray = new JSONArray();
        boolean isDialCodePossible = false;
        for (int i = 0; i < myWebSocketService.usersList.length(); i++) {
            if (myWebSocketService.usersList.optJSONObject(i).optString("dialCode", "").contains(dialCode)) {
                isDialCodePossible = true;
                if (myWebSocketService.usersList.optJSONObject(i).optString("dialCode", "").equals(dialCode))
                    filteredArray.put(myWebSocketService.usersList.optJSONObject(i));
            }
        }
        if (!isDialCodePossible) {
            dialCodeEditText.setText("");
            dialCodeResultText.setText(R.string.dial_code_does_not);
            dialCodeResultSubText.setText(R.string.please_try_again);
            dialCodeResultText.setTextColor(getColor(R.color.cplex_error));
            dialCodeResultSubText.setTextColor(getColor(R.color.cplex_error));
        } else if (filteredArray.length() == 1) {
            //call
            hideKeyboard(dialCodeEditText);
            isScreenSaver = false;
            handler2.removeCallbacks(r2);
            if (!isSendingSecurityVideo)
                videoCapturerAndroid.startCapture(240, 320, 5);
            localVideoTrack.addSink(localVideoView);

            JSONObject objText = filteredArray.optJSONObject(0);

            findViewById(R.id.user_list_cardview).setVisibility(View.GONE);
            findViewById(R.id.dialcode_cardview).setVisibility(View.GONE);
            findViewById(R.id.calling_screen_card).setVisibility(View.VISIBLE);

            localVideoView.addFrameListener(new EglRenderer.FrameListener() {
                @Override
                public void onFrame(Bitmap bitmap) {
                    runOnUiThread(() -> {
                        myWebSocketService.tryCallingUser(objText, getBase64Image(bitmap));
                        localVideoView.removeFrameListener(this);
                    });
                }
            }, 1);
        }
    }

    @Override
    protected void onResume() {
        new Handler(Looper.getMainLooper()).postDelayed(()->{
            if(BuildConfig.IS_PRODUCTION) {
                startLockTask();
            }
        }, 1000);
        super.onResume();
    }

    private void hideKeyboard(View v) {
        InputMethodManager keyboardManager = (InputMethodManager) getSystemService(
                Context.INPUT_METHOD_SERVICE);
        keyboardManager.hideSoftInputFromWindow(v.getWindowToken(), 0);
    }

    private void showKeyboard(View v) {
        InputMethodManager keyboardManager = (InputMethodManager) getSystemService(
                Context.INPUT_METHOD_SERVICE);
        keyboardManager.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT);
    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        if (ssTimeout > 0 && isScreenSaver) {
            handler2.removeCallbacks(r2);
            handler2.postDelayed(r2, ssTimeout * 1000);
        }
    }

    @Override
    public void onBackPressed() {
        // Don't want user to click back button to kill app and make changes to our device
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.KEEP_SCREEN_ON
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN);
        new Handler(Looper.getMainLooper()).postDelayed(() -> getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.KEEP_SCREEN_ON
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN), 100);
        super.onWindowFocusChanged(hasFocus);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        View v = getCurrentFocus();
        if (v instanceof EditText) {
            v.clearFocus();
            hideKeyboard(v);
        }
        return super.dispatchTouchEvent(event);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void didreceivedKioskConfig() {
        isIntercomEnabled = myWebSocketService.kioskConfig.has("displayConfiguration") && myWebSocketService.kioskConfig.optJSONObject("displayConfiguration").has("isIntercomEnabled") && myWebSocketService.kioskConfig.optJSONObject("displayConfiguration").optBoolean("isIntercomEnabled");
        if (isIntercomEnabled) {
            findViewById(R.id.menubtn1).setVisibility(View.GONE);
            findViewById(R.id.user_list_cardview).setVisibility(View.GONE);
            findViewById(R.id.toggle_button_group).setVisibility(View.GONE);
            ((LinearLayout) findViewById(R.id.welcome_text)).setGravity(Gravity.CENTER);
            findViewById(R.id.callingButtons).setVisibility(View.VISIBLE);
        } else {
            ConstraintLayout corosl = findViewById(R.id.corosl);
            corosl.setVisibility(View.GONE);
            findViewById(R.id.callingButtons).setVisibility(View.GONE);
        }
        myWebSocketService.getUsersNames();

        String val1 = Objects.requireNonNull(myWebSocketService.kioskConfig.optString("name"));
        kioskName.setText(val1);

        JSONObject fullAddress = Objects.requireNonNull(myWebSocketService.kioskConfig.optJSONObject("displayConfiguration")).optJSONObject("address");
        runOnUiThread(() -> {
            kioskName.setText(sp.getString("ELPNAME", ""));
            if (fullAddress != null) {
                siteName.setText(fullAddress.optString("site", ""));
                TextView buildingText = findViewById(R.id.buildingText);

                if (fullAddress.optString("buildingName", "").length() > 0) {
                    buildingText.setText(fullAddress.optString("buildingName"));
                    buildingText.setVisibility(View.VISIBLE);
                } else {
                    buildingText.setVisibility(View.GONE);
                }

                String[] addr = {fullAddress.optString("streetAddr", ""),
                        fullAddress.optString("city", ""),
                        fullAddress.optString("province", ""),
                        fullAddress.optString("postalCode", "")};

                addr = Arrays.stream(addr)
                        .filter(s -> (s != null && s.length() > 0))
                        .toArray(String[]::new);

                addressLine.setText(String.join(", ", addr));


                LinearLayout toggleBtnLayout = findViewById(R.id.toggle_btn_layout);
                toggleBtnLayout.setVisibility(View.VISIBLE);
            }
        });

        if (myWebSocketService.kioskConfig.optJSONObject("displayConfiguration").optBoolean("isWeatherEnabled")) {
            String cityName = myWebSocketService.kioskConfig.optJSONObject("displayConfiguration").optJSONObject("weather").optString("city");
            String country = myWebSocketService.kioskConfig.optJSONObject("displayConfiguration").optJSONObject("weather").optString("country");
//                    + "," + (myWebSocketService.kioskConfig.optJSONObject("displayConfiguration").optJSONObject("weather").optString("country") == "US" ? "US" : "CA");

            new Thread(() -> {
                JSONObject weatherData = getJSON(cityName, country);
                runOnUiThread(() -> {
                    if (weatherData != null) {
                        runOnUiThread(() -> {
                            ImageView weatherLogo = findViewById(R.id.weather_logo);
                            TextView weatherCity = findViewById(R.id.weather_city);
                            TextView weatherTemp = findViewById(R.id.weather_temp);

                            weatherCity.setText(weatherData.optString("name"));
                            weatherTemp.setText(Objects.requireNonNull(weatherData.optJSONObject("main")).optString("temp") + (char) 0x00B0 + (country.equals("Canada") ? " C" : " F"));

                            Picasso.get()
                                    .load("http://openweathermap.org/img/w/" + Objects.requireNonNull(weatherData.optJSONArray("weather")).optJSONObject(0).optString("icon") + ".png")
                                    .into(weatherLogo);
                            findViewById(R.id.weather_layout).setVisibility(View.VISIBLE);
                        });
                    } else {
                        runOnUiThread(() -> {
                            findViewById(R.id.weather_layout).setVisibility(View.INVISIBLE);
                        });
                    }
                });
            }).start();
        } else {
            runOnUiThread(() -> {
                findViewById(R.id.weather_layout).setVisibility(View.INVISIBLE);
            });
        }

        JSONArray imgList = myWebSocketService.kioskConfig.optJSONObject("displayConfiguration").optJSONArray("imgCarousel");
        if (imgList != null && imgList.length() > 0) {
            runOnUiThread(() -> {
                if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    findViewById(R.id.time_header).setPadding(0, 0, 0, 0);
                    findViewById(R.id.corosl).setVisibility(View.VISIBLE);
                    setupImageCarousel(imgList);
                }
            });
        }
        if (isIntercomEnabled) {
            Button intercomBtn = findViewById(R.id.intercomBtn);
            intercomBtn.setOnClickListener(v -> {
                myWebSocketService.callGuard(45000, null);
                findViewById(R.id.callingButtons).setVisibility(View.GONE);
                findViewById(R.id.calling_screen_card).setVisibility(View.VISIBLE);
                ((TextView) findViewById(R.id.username_text)).setText("Guard");
            });
            JSONArray imagesList = myWebSocketService.kioskConfig.optJSONObject("displayConfiguration").optJSONArray("imgCarousel");
            if (imagesList != null && imagesList.length() > 0) {
                runOnUiThread(() -> {
                    findViewById(R.id.time_header).setPadding(0, 0, 0, 0);
                    findViewById(R.id.corosl).setVisibility(View.VISIBLE);
                    setupImageCarousel(imagesList);
                });
            }
        }

        JSONArray imgScreenSaver = myWebSocketService.kioskConfig.optJSONObject("displayConfiguration").optJSONArray("imgCarousel");
        if (imgScreenSaver != null && imgScreenSaver.length() > 0) {
            runOnUiThread(() -> {
                //  String img_name= imgScreenSaver.optJSONObject(0).optString("img");
                String img_url = "https://elp." + sp.getString("SERVICEGROUP", "development") + ".condoplex.net:38500/img/" + imgList.optJSONObject(0).optString("img");
                Picasso.get()
                        .load(img_url)
                        .into(imgss);
            });
        }

        String companyLogo = Objects.requireNonNull(myWebSocketService.kioskConfig.optJSONObject("displayConfiguration")).optString("logo");
        ImageView logoImage = findViewById(R.id.company_logo);

        runOnUiThread(() -> {
            if (!companyLogo.equals("") && companyLogo.length() > 1) {
                logoImage.setImageBitmap(getBitmapImage(companyLogo));
            } else logoImage.setVisibility(View.GONE);
        });


        JSONObject style = Objects.requireNonNull(myWebSocketService.kioskConfig.optJSONObject("displayConfiguration")).optJSONObject("style");
        if (style != null) {
            runOnUiThread(() -> {
                findViewById(R.id.root).setBackgroundColor(Color.parseColor(style.optString("backcolor")));

                if (!style.optString("cardbackcolor").equals("#black"))
                    findViewById(R.id.menubtn1).setBackgroundTintList(ColorStateList.valueOf(Color.parseColor(style.optString("cardbackcolor"))));
                else {
                    findViewById(R.id.menubtn1).setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#000000")));
                }
//                findViewById(R.id.menubtn1).setBackgroundResource(R.drawable.menu_btn);

                TextView time1 = findViewById(R.id.time1);
                TextView time2 = findViewById(R.id.time2);
                TextView dateText = findViewById(R.id.dateText);
                TextView welcometext = findViewById(R.id.welcomeText);
                TextView siteText = findViewById(R.id.siteName);
                @SuppressLint("CutPasteId") TextView buildingtext1 = findViewById(R.id.buildingText);
                TextView addresstext = findViewById(R.id.addressText);

                materialButtonToggleGroup = findViewById(R.id.toggle_button_group);

                if (style.optString("textColor").equals("white")) {
                    time1.setTextColor(Color.WHITE);
                    time2.setTextColor(Color.WHITE);
                    dateText.setTextColor(Color.WHITE);
                    welcometext.setTextColor(Color.WHITE);
                    siteText.setTextColor(Color.WHITE);
                    buildingtext1.setTextColor(Color.WHITE);
                    addresstext.setTextColor(Color.WHITE);
                } else {
                    time1.setTextColor(Color.BLACK);
                    time2.setTextColor(Color.BLACK);
                    dateText.setTextColor(Color.BLACK);
                    welcometext.setTextColor(Color.BLACK);
                    siteText.setTextColor(Color.BLACK);
                    buildingtext1.setTextColor(Color.BLACK);
                    addresstext.setTextColor(Color.BLACK);
                }
            });
        }

        boolean isVolumeDisplayed = Objects.requireNonNull(myWebSocketService.kioskConfig.optJSONObject("displayConfiguration")).optBoolean("displayVolume");
        runOnUiThread(() -> {
            if (!isVolumeDisplayed) {
                findViewById(R.id.volume_btn).setVisibility(View.GONE);
            } else {
                findViewById(R.id.volume_btn).setVisibility(View.VISIBLE);
                findViewById(R.id.language_btn).setVisibility(View.VISIBLE);
            }
        });

        int defaultVolume = myWebSocketService.kioskConfig.optInt("defaultVolume");
        myWebSocketService.kioskVolume = defaultVolume;
        int vol = defaultVolume * 15 / 100;
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, vol, 0);

        boolean x = !myWebSocketService.kioskConfig.has("shouldSendVideo") || myWebSocketService.kioskConfig.optBoolean("shouldSendVideo");
        if (x && myWebSocketService.kioskConfig != null && myWebSocketService.kioskConfig.has("securityVideo")) {
            if (!isVSServiceRunning(VideoStreamingService.class))
                runOnUiThread(this::startLiveStreaming);
            else if (!myVSservice.myRoom.equals(Objects.requireNonNull(myWebSocketService.kioskConfig.optJSONObject("securityVideo")).optString("room")))
                myVSservice.onDestroy();
        } else if (isVSServiceRunning(VideoStreamingService.class) && myVSservice != null)
            myVSservice.onDestroy();

        if (myWebSocketService.kioskConfig != null
                && myWebSocketService.kioskConfig.optJSONObject("displayConfiguration").has("isScreenSaverEnabled")
                && myWebSocketService.kioskConfig.optJSONObject("displayConfiguration").optBoolean("isScreenSaverEnabled")) {
            JSONObject ssConfig = myWebSocketService.kioskConfig.optJSONObject("displayConfiguration").optJSONObject("screenSaverConfig");
            ssTimeout = ssConfig.optInt("timeout");
            handler2.postDelayed(r2, ssTimeout * 1000);
        } else {
            isScreenSaver = false;
            handler2.removeCallbacks(r2);
            imgss.setVisibility(View.GONE);
            text_ss.setVisibility(View.GONE);
        }

        if (myWebSocketService.kioskConfig.has("internet") && Objects.requireNonNull(myWebSocketService.kioskConfig.optJSONObject("internet")).has("timezone")) {
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
                ComponentName adminName = new ComponentName(this, MyDeviceAdminReceiver.class);
                dpm.setGlobalSetting(adminName, Settings.Global.AUTO_TIME_ZONE, "0");
                dpm.setTimeZone(adminName, Objects.requireNonNull(myWebSocketService.kioskConfig.optJSONObject("internet")).optString("timezone"));
            }
//            else {
//                AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
//                am.setTimeZone(myWebSocketService.kioskConfig.optJSONObject("internet").optString("timezone"));
//            }
        }

        if (myWebSocketService.kioskConfig.optBoolean("showPinCodeBtn", true)) {
            findViewById(R.id.resident_btn).setVisibility(View.VISIBLE);
        } else findViewById(R.id.resident_btn).setVisibility(View.GONE);

        if (Objects.requireNonNull(myWebSocketService.kioskConfig.optJSONObject("displayConfiguration")).has("defaultLanguage")) {
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                setLocale(Objects.requireNonNull(myWebSocketService.kioskConfig.optJSONObject("displayConfiguration")).optString("defaultLanguage", "en"));
            }, 2000);
        }

        if (!Objects.requireNonNull(myWebSocketService.kioskConfig.optJSONObject("displayConfiguration")).optBoolean("showNeedAssist")
                || Objects.requireNonNull(Objects.requireNonNull(myWebSocketService.kioskConfig.optJSONObject("displayConfiguration")).optJSONArray("buttons")).length() < 1
                || isIntercomEnabled) {
            findViewById(R.id.menubtn1).setVisibility(View.GONE);
            LinearLayout callingButtonsLayout = findViewById(R.id.callingButtons);

            JSONArray buttonsArray = myWebSocketService.kioskConfig.optJSONObject("displayConfiguration").optJSONArray("buttons");

            // Find the intercom button
            View intercomBtn = findViewById(R.id.intercomBtn);

            // Remove all views from callingButtonsLayout except intercomBtn
            for (int i = callingButtonsLayout.getChildCount() - 1; i >= 0; i--) {
                View child = callingButtonsLayout.getChildAt(i);
                if (child != intercomBtn) {
                    callingButtonsLayout.removeViewAt(i);
                }
            }

            if (buttonsArray != null) {

                findViewById(R.id.intercomBtn).setVisibility(View.GONE);

                int numButtons = buttonsArray.length();
                int numColumns = 2;
                int numRows = (int) Math.ceil((double) numButtons / numColumns);

                for (int i = 0; i < numRows; i++) {
                    LinearLayout rowLayout = new LinearLayout(this);
                    rowLayout.setLayoutParams(new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                    ));
                    rowLayout.setOrientation(LinearLayout.HORIZONTAL);
                    rowLayout.setGravity(Gravity.CENTER);

                    for (int j = 0; j < numColumns; j++) {
                        int index = i * numColumns + j;
                        if (index < numButtons) {
                            JSONObject buttonData = buttonsArray.optJSONObject(index);
                            if (buttonData != null) {
                                Button button = new Button(this, null, android.R.attr.buttonStyle);
                                LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                                        LinearLayout.LayoutParams.WRAP_CONTENT,
                                        LinearLayout.LayoutParams.WRAP_CONTENT
                                );
                                layoutParams.setMargins(10, 10, 10, 10);
                                button.setLayoutParams(layoutParams);
                                // Set label as text for the button
                                button.setTextColor(Color.WHITE);
                                button.setText(buttonData.optString("label"));

                                String iconName = buttonData.optString("icon");
                                int iconResId = getResources().getIdentifier(iconName, "drawable", getPackageName());
                                if (iconResId != 0) {
                                    button.setCompoundDrawablesWithIntrinsicBounds(iconResId, 0, 0, 0);
                                }

                                // Apply style
                                button.setBackgroundResource(R.drawable.intercom_btn_background);
                                button.setTextSize(30);
                                button.setPadding(50, 50, 50, 50);
                                button.setMinHeight(300);
                                button.setMinWidth(320);
                                button.setOnClickListener(new View.OnClickListener() {
                                    @Override
                                    public void onClick(View v) {
                                        onClickIntercomBtn(buttonData);
                                    }
                                });
                                rowLayout.addView(button);
                            }
                        }
                    }
                    callingButtonsLayout.addView(rowLayout);
                }
            } else {
                findViewById(R.id.intercomBtn).setVisibility(View.VISIBLE);
            }
        } else {
            findViewById(R.id.menubtn1).setVisibility(View.VISIBLE);
        }
    }

    private void onClickIntercomBtn(JSONObject buttonData) {

        if (buttonData.has("phoneNumber") && buttonData.optString("phoneNumber").length() == 10) {
            myWebSocketService.tryCallingUser(buttonData, "");
        } else {
            myWebSocketService.callGuard(45000, buttonData);
        }
        findViewById(R.id.callingButtons).setVisibility(View.GONE);
        findViewById(R.id.calling_screen_card).setVisibility(View.VISIBLE);
        ((TextView) findViewById(R.id.username_text)).setText(buttonData.optString("label", "Guard"));
    }

    public Bitmap getBitmapImage(String myImg) {
        if (myImg.length() > 0) {
            String base64Image = myImg.split(",")[1];
            byte[] decodedString = Base64.decode(base64Image, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
        } else {
            return null;
        }
    }

    public String getBase64Image(Bitmap bitmap) {
        if (bitmap != null) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 50, baos);
            byte[] imageBytes = baos.toByteArray();
            String imageString = Base64.encodeToString(imageBytes, Base64.DEFAULT);
            return "data:image/jpeg;base64," + imageString;
        } else return "";
    }

    private void setupImageCarousel(JSONArray imgList) {
        SliderView sliderView = findViewById(R.id.imageSlider);
        ImageButton leftSlider = findViewById(R.id.left_slider_btn);
        ImageButton rightSlider = findViewById(R.id.right_slider_btn);

        imageSliderAdapter adapter1 = new imageSliderAdapter();
        sliderView.setSliderAdapter(adapter1);

        sliderView.setIndicatorEnabled(true);
        sliderView.setIndicatorAnimation(IndicatorAnimationType.SLIDE); //set indicator animation by using SliderLayout.IndicatorAnimations. :WORM or THIN_WORM or COLOR or DROP or FILL or NONE or SCALE or SCALE_DOWN or SLIDE and SWAP!!
        sliderView.setSliderTransformAnimation(SliderAnimations.SIMPLETRANSFORMATION);
        sliderView.setAutoCycleDirection(SliderView.AUTO_CYCLE_DIRECTION_RIGHT);
        sliderView.setIndicatorSelectedColor(Color.WHITE);
        sliderView.setIndicatorUnselectedColor(Color.GRAY);
        sliderView.setScrollTimeInSec(5);
        sliderView.setAutoCycle(true);
        sliderView.startAutoCycle();

        List<SliderItem> sliderItemList = new ArrayList<>();
        for (int i = 0; i < imgList.length(); i++) {
            SliderItem sliderItem = new SliderItem();
            sliderItem.setDescription(imgList.optJSONObject(i).optString("text"));
            sliderItem.setImageUrl("https://elp." + sp.getString("SERVICEGROUP", "development") + ".condoplex.net:38500/img/" + imgList.optJSONObject(i).optString("img"));
            //sliderItem.setImageUrl("https://elp.elpmirror" + ".condoplex.net:38500/img/" + imgList.optJSONObject(i).optString("img"));
            sliderItemList.add(sliderItem);
        }
        adapter1.renewItems(sliderItemList);

        leftSlider.setOnClickListener(v -> sliderView.slideToPreviousPosition());
        rightSlider.setOnClickListener(v -> sliderView.slideToNextPosition());
    }

    @Override
    public void gotUserList() {
        new Thread(() -> {
            listGroup.clear();
            for (int x = 0; x < myWebSocketService.usersList.length(); x++) {
                try {
                    listGroup.add((JSONObject) myWebSocketService.usersList.get(x));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            listGroup.sort(Comparator.comparing(o -> (o.optString("displayName").toLowerCase())));

            new Handler(Looper.getMainLooper()).post(() -> runOnUiThread(() -> {
                adapter = new UserListAdapter(listGroup);
                usersList.setAdapter(adapter);

                LinearLayout indexLayout = findViewById(R.id.user_list_index_layout);
                indexLayout.removeAllViewsInLayout();
                for (int y = 0; y < adapter.sections.length; y++) {
                    TextView indexText = new TextView(this);
                    indexText.setText(adapter.sections[y]);
                    indexText.setId(y);
                    indexText.setGravity(Gravity.CENTER_HORIZONTAL);
                    indexText.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                    indexLayout.addView(indexText);
                }
            }));
        }).start();
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void gotAccessMessage(JSONObject objText) {
        String message = objText.optString("message");
        runOnUiThread(() -> {
            if (message.equalsIgnoreCase("access denied")) {
                pinResultText.setText(R.string.access_denied);
                pinResultSubText.setText(R.string.valid_pin);
                pinResultText.setTextColor(getColor(R.color.cplex_error));
                pinResultSubText.setTextColor(getColor(R.color.cplex_error));

                dialCodeResultText.setText(R.string.access_denied);
                dialCodeResultSubText.setText(R.string.valid_pin);
                dialCodeResultText.setTextColor(getColor(R.color.cplex_error));
                dialCodeResultSubText.setTextColor(getColor(R.color.cplex_error));
                new Handler().postDelayed(this::shouldRestrictUser, 2000);
            } else if (message.equalsIgnoreCase("access granted")) {
                myWebSocketService.openRelay(0, 5000);
                myWebSocketService.openRelay(1, 5000);
                if (tempPinCode != null && !tempPinCode.equals("")) {
                    myWebSocketService.sendWiegandOutput(tempPinCode, "pinFacilityCode");
                    tempPinCode = "";
                }
                pinResultText.setText(R.string.access_granted);
                pinResultSubText.setText(R.string.door_unlocked);
                pinResultText.setTextColor(getColor(R.color.green));
                pinResultSubText.setTextColor(getColor(R.color.green));

                dialCodeResultText.setText(R.string.access_granted);
                dialCodeResultSubText.setText(R.string.door_unlocked);
                dialCodeResultText.setTextColor(getColor(R.color.green));
                dialCodeResultSubText.setTextColor(getColor(R.color.green));

                //reset counter
                sp.edit().putInt("wrongAttempts", 0).apply();
            }
        });
    }

    @Override
    public void gotAlertDialog(JSONObject objText) {
        if (materialButtonToggleGroup.getCheckedButtonId() != R.id.guest_btn) return;

//        playAudio("https://elp." + sp.getString("SERVICEGROUP", "development") + ".condoplex.net:38500" + objText.optString("audio"));
        String message = objText.optString("message");
        runOnUiThread(() -> {
            AlertDialog.Builder builder1 = new AlertDialog.Builder(MainActivity.this, R.style.customDesignForAlert);

            if (message.equalsIgnoreCase("access granted")) {
                builder1.setMessage(R.string.access_granted);
            } else builder1.setMessage(R.string.access_denied);
            builder1.setCancelable(true);
            builder1.setNegativeButton("Close", (dialog, id) -> dialog.cancel());

            AlertDialog alert11 = builder1.create();
            alert11.show();
            TextView textView = (TextView) alert11.findViewById(android.R.id.message);
            textView.setTextSize(32);

            Button negativeButton = alert11.getButton(AlertDialog.BUTTON_NEGATIVE);
            negativeButton.setTextColor(Color.parseColor("#ffffff"));
            negativeButton.setBackgroundColor(Color.parseColor("#228B22"));
            final Handler handler = new Handler();
            final Runnable runnable = () -> {
                if (alert11.isShowing()) {
                    alert11.dismiss();
                }
            };
            alert11.setOnDismissListener(dialog -> handler.removeCallbacks(runnable));
            handler.postDelayed(runnable, 5000);
        });
    }

    public void playAudio(String audioLink) {
        Log.e("MyError==", audioLink);
        MediaPlayer mediaPlayer = new MediaPlayer();
        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        try {
            mediaPlayer.setDataSource(audioLink);
            mediaPlayer.prepare();
            mediaPlayer.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void showCallingFromInterface() {
        runOnUiThread(this::showCallingActivity);
    }

//    @Override
//    public void createPeerConnectionFromInterface() {
//
//    }

    @Override
    public void acceptCallFromInterface() {
        onAcceptCall();
    }

    @Override
    public void handleHangupFromWs() {
        runOnUiThread(this::hangup);
    }

    @Override
    public void createOfferForCallFromWS() {
        createOfferForCall();
    }

    @Override
    public void createOfferForVSFromWS() {
        createOffer();
    }

    @Override
    public void setRemoteDescriptionForVSFromWS() {
        setRemoteDescriptionForVS();
    }

    @Override
    public void setRemoteDescriptionForCallFromWS() {
        setRemoteDescriptionForCall();
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void handleRingingStatusFromWS() {
        runOnUiThread(() -> ((TextView) findViewById(R.id.call_status_text)).setText(R.string.ringing));
    }

    @Override
    public void restartActivity() {
        this.restart();
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void updateViewsForSipCallFromWS() {
        runOnUiThread(() -> {
//            updateViews();
            ((TextView) findViewById(R.id.call_status_text)).setText(R.string.duration);
            this.findViewById(R.id.call_duration).setVisibility(View.VISIBLE);
            ((Chronometer) this.findViewById(R.id.call_duration)).setBase(SystemClock.elapsedRealtime());
            ((Chronometer) this.findViewById(R.id.call_duration)).start();
            myWebSocketService.isCallConnected = true;
        });
    }

    private void getReadyForVideoCalls() {
        localVideoView = findViewById(R.id.local_view);
        remoteVideoView = findViewById(R.id.remote_view);

        localVideoView.init(rootEglBase.getEglBaseContext(), null);
        remoteVideoView.init(rootEglBase.getEglBaseContext(), null);

        localVideoView.setZOrderMediaOverlay(true);
//        remoteVideoView.setZOrderMediaOverlay(true);

        // To initialize peerconnectionFactory
        PeerConnectionFactory.InitializationOptions initializationOptions =
                PeerConnectionFactory.InitializationOptions.builder(this)
                        .createInitializationOptions();
        PeerConnectionFactory.initialize(initializationOptions);

        //Create a new PeerConnectionFactory instance - using Hardware encoder and decoder.
        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        DefaultVideoEncoderFactory defaultVideoEncoderFactory = new DefaultVideoEncoderFactory(
                rootEglBase.getEglBaseContext(), true, false);
        DefaultVideoDecoderFactory defaultVideoDecoderFactory = new DefaultVideoDecoderFactory(rootEglBase.getEglBaseContext());
        peerConnectionFactory1 = PeerConnectionFactory.builder()
                .setOptions(options)
                .setVideoEncoderFactory(defaultVideoEncoderFactory)
                .setVideoDecoderFactory(defaultVideoDecoderFactory)
                .createPeerConnectionFactory();

        //to create a VideoCapturer instance.
//        public VideoCapturer videoCapturerAndroid;
        videoCapturerAndroid = createCameraCapturer(new Camera1Enumerator(false));

//        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
//
//        // Set the audio mode to MODE_IN_COMMUNICATION
//        audioManager.setMode(AudioManager.MODE_IN_CALL);
//        audioManager.setMicrophoneMute(false);
//        audioManager.setSpeakerphoneOn(true);
        //Create MediaConstraints
        MediaConstraints audioConstraints = new MediaConstraints();
//        MediaConstraints videoConstraints = new MediaConstraints();

        //Create a VideoSource instance
        if (videoCapturerAndroid != null) {
            SurfaceTextureHelper surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", rootEglBase.getEglBaseContext());
            videoSource = peerConnectionFactory1.createVideoSource(videoCapturerAndroid.isScreencast());
            videoCapturerAndroid.initialize(surfaceTextureHelper, this, videoSource.getCapturerObserver());
            localVideoTrack = peerConnectionFactory1.createVideoTrack("ARDAMSv0", videoSource);
        }

        //create an AudioSource instance
        AudioSource audioSource = peerConnectionFactory1.createAudioSource(audioConstraints);
        localAudioTrack = peerConnectionFactory1.createAudioTrack("ARDAMSa0", audioSource);
        localAudioTrack.setEnabled(true);

        //create surface renderer, init it and add the renderer to the track
        localVideoView.setMirror(false);
        localVideoView.setEnableHardwareScaler(true);
//        localVideoTrack.addSink(localVideoView);
        if(localVideoTrack != null) {
            localVideoTrack.setEnabled(true);
        }

        remoteVideoView.setEnableHardwareScaler(true);
    }

    // all about live stream from kiosk to guard
    private void startLiveStreaming() {
        createPeerConnection();

        if (myWebSocketService.kioskConfig.optJSONObject("securityVideo") != null) {

            //start video capture and add sink
            videoCapturerAndroid.startCapture(240, 320, 5);
            isSendingSecurityVideo = true;

            JSONObject objText = myWebSocketService.kioskConfig.optJSONObject("securityVideo");
            Intent VSSIntent = new Intent(this, VideoStreamingService.class);
            assert objText != null;
            VSSIntent.putExtra("plugin", objText.optString("plugin"));
            VSSIntent.putExtra("server", objText.optString("server"));
            VSSIntent.putExtra("port", objText.optString("port"));
            VSSIntent.putExtra("username", objText.optString("username"));
            VSSIntent.putExtra("token", objText.optString("token"));
            VSSIntent.putExtra("room", objText.optString("room"));

//            new PingAndLogHelper(getApplicationContext(), "log",
//                    sp.getString("SERVICEGROUP", ""),
//                    sp.getString("ELPNAME", ""),
//                    BuildConfig.VERSION_NAME,
//                    "VideoStreaming",
//                    "Server: " + objText.optString("server") + ":" + objText.optString("port") + "%0ARoom: " + objText.optString("room"));

            startService(VSSIntent);

            isBoundvs = getApplicationContext().bindService(VSSIntent, serviceConnectorVS, 0);
        }
    }

    private void createPeerConnection() {
        PeerConnection.RTCConfiguration rtcConfig =
                new PeerConnection.RTCConfiguration(peerIceServers);
        rtcConfig.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED;
        rtcConfig.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE;
        rtcConfig.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE;
        rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;
        rtcConfig.keyType = PeerConnection.KeyType.ECDSA;
        localPeer1 = peerConnectionFactory1.createPeerConnection(rtcConfig, new CustomPeerConnectionObserver("localPeerCreation") {
            @Override
            public void onIceCandidate(IceCandidate iceCandidate) {
                super.onIceCandidate(iceCandidate);
            }

            @Override
            public void onAddStream(MediaStream mediaStream) {
                super.onAddStream(mediaStream);
            }
        });
        addStreamToLocalPeer();
    }

    private void setRemoteDescriptionForVS() {
        localPeer1.setRemoteDescription(new CustomSdpObserver("localSetRemote"), new SessionDescription(SessionDescription.Type.ANSWER, myVSservice.myJsep.optString("sdp")));
    }

    /**
     * Adding the stream to the localpeer
     */
    private void addStreamToLocalPeer() {
        //creating local mediastream
        MediaStream stream = peerConnectionFactory1.createLocalMediaStream("102");
        //we dont want audio while sending video to admin app
//        stream.addTrack(localAudioTrack);
        stream.addTrack(localVideoTrack);
        localPeer1.addStream(stream);
    }

    private void createOffer() {
        new Handler(Looper.getMainLooper()).postDelayed(() -> localPeer1.createOffer(new CustomSdpObserver("localCreateOffer") {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                super.onCreateSuccess(sessionDescription);
                localPeer1.setLocalDescription(new CustomSdpObserver("localSetLocal"), sessionDescription);
//                    remotePeer.setRemoteDescription(new CustomSdpObserver("remoteSetRemote"), sessionDescription);
//                    myService.acceptCall(sessionDescription);
                myVSservice.publisherCreateOffer(sessionDescription);
            }
        }, new MediaConstraints()), 100);
    }


    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private VideoCapturer createCameraCapturer(CameraEnumerator enumerator) {
        final String[] deviceNames = enumerator.getDeviceNames();
        // Trying to find a front facing camera!
        for (String deviceName : deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);
                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }
        // We were not able to find a front cam. Look for other cameras
        for (String deviceName : deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);
                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }
        return null;
    }

    @SuppressLint("SetTextI18n")
    private void showCallingActivity() {
        imgss.setVisibility(View.GONE);
        text_ss.setVisibility(View.GONE);

        //to start capturing and showing on video view
        isScreenSaver = false;
        handler2.removeCallbacks(r2);
        if (!isSendingSecurityVideo && videoCapturerAndroid != null)
            videoCapturerAndroid.startCapture(240, 320, 5);
        if(localVideoTrack != null) {
            localVideoTrack.addSink(localVideoView);
        }

        residentResetHandler.removeCallbacks(myRunnable);
        findViewById(R.id.callingButtons).setVisibility(View.GONE);
        findViewById(R.id.user_list_cardview).setVisibility(View.GONE);

        findViewById(R.id.calling_screen_card).setVisibility(View.VISIBLE);
        Intent i = new Intent(this, JanusConnectionService.class);
        isBoundJanus = getApplicationContext().bindService(i, serviceConnectorJanus, 0);

        createPeerConnectionForCall();

        if (myWebSocketService.callingUserDetails != null) {
            String userName = myWebSocketService.callingUserDetails.optString("displayName");
            ((TextView) findViewById(R.id.username_text)).setText(toTitleCase(userName));
        } else {
            ((TextView) findViewById(R.id.username_text)).setText("Guard");
//            findViewById(R.id.end_call).setVisibility(View.GONE);
            ((TextView) findViewById(R.id.call_status_text)).setText(R.string.calling);

        }
    }

    /**
     * Creating the local peerconnection instance
     */
    private void createPeerConnectionForCall() {
        PeerConnection.RTCConfiguration rtcConfig =
                new PeerConnection.RTCConfiguration(peerIceServers);
        rtcConfig.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED;
        rtcConfig.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE;
        rtcConfig.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE;
        rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;
        rtcConfig.keyType = PeerConnection.KeyType.ECDSA;
        localPeer = peerConnectionFactory1.createPeerConnection(rtcConfig, new CustomPeerConnectionObserver("localPeerCreation") {
            @Override
            public void onIceCandidate(IceCandidate iceCandidate) {
                super.onIceCandidate(iceCandidate);
            }

            @Override
            public void onAddStream(MediaStream mediaStream) {
                super.onAddStream(mediaStream);
                if (myService.myPlugin.contains("videocall"))
                    gotRemoteStreamForCall(mediaStream);
            }
        });
        addStreamToLocalPeerForCall();
    }

    @SuppressLint("SetTextI18n")
    private void setRemoteDescriptionForCall() {
        if (((TextView) findViewById(R.id.call_status_text)).getText().equals(getString(R.string.ringing))) {
            localPeer.setRemoteDescription(new CustomSdpObserver("localSetRemote"), new SessionDescription(SessionDescription.Type.ANSWER, myService.myJsep.optString("sdp")));
            if (myService.myPlugin.contains("videocall"))
                runOnUiThread(() -> {
                    updateViews();
                    ((TextView) findViewById(R.id.call_status_text)).setText(R.string.duration);
                    this.findViewById(R.id.call_duration).setVisibility(View.VISIBLE);
                    ((Chronometer) this.findViewById(R.id.call_duration)).setBase(SystemClock.elapsedRealtime());
                    ((Chronometer) this.findViewById(R.id.call_duration)).start();
                    myWebSocketService.isCallConnected = true;
                });
        } else {
            localPeer.setRemoteDescription(new CustomSdpObserver("localSetRemote"), new SessionDescription(SessionDescription.Type.OFFER, myService.myJsep.optString("sdp")));
        }
    }

    private void addStreamToLocalPeerForCall() {
        //creating local mediastream
        MediaStream stream = peerConnectionFactory1.createLocalMediaStream("102");
        stream.addTrack(localAudioTrack);
        if(localVideoTrack != null) {
            stream.addTrack(localVideoTrack);
        }
        localPeer.addStream(stream);
    }

    /**
     * Received remote peer's media stream. we will get the first video track and render it
     */
    private void gotRemoteStreamForCall(MediaStream stream) {
        //we have remote video stream. add to the renderer.
        if (stream.videoTracks.size() < 1) return;
        final VideoTrack videoTrack = stream.videoTracks.get(0);
        runOnUiThread(() -> {
            try {
                remoteVideoView.setVisibility(View.VISIBLE);
                videoTrack.addSink(remoteVideoView);
                AudioManager audioManager = (AudioManager) getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
                audioManager.setMode(AudioManager.MODE_IN_CALL);
                audioManager.setSpeakerphoneOn(true);
//                Log.e("=====", "Setting microphone true");
//                audioManager.setMicrophoneMute(false);
                updateViews();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void createOfferForCall() {
        new Handler(Looper.getMainLooper()).postDelayed(() -> localPeer.createOffer(new CustomSdpObserver("localCreateOffer") {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                super.onCreateSuccess(sessionDescription);
                localPeer.setLocalDescription(new CustomSdpObserver("localSetLocal1"), sessionDescription);
                myService.callUser(sessionDescription);
            }
        }, new MediaConstraints()), 100);
    }

    @SuppressLint("SetTextI18n")
    private void onAcceptCall() {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            localPeer.createAnswer(new CustomSdpObserver("localCreateAns1") {
                @Override
                public void onCreateSuccess(SessionDescription sessionDescription) {
                    super.onCreateSuccess(sessionDescription);
                    localPeer.setLocalDescription(new CustomSdpObserver("localSetLocal1"), sessionDescription);
                    myService.acceptCall(sessionDescription);
                }

                @Override
                public void onCreateFailure(String s) {
                    super.onCreateFailure(s);
                }
            }, new MediaConstraints());
            updateViews();
            ((TextView) findViewById(R.id.call_status_text)).setText(R.string.duration);
            this.findViewById(R.id.call_duration).setVisibility(View.VISIBLE);
            ((Chronometer) this.findViewById(R.id.call_duration)).setBase(SystemClock.elapsedRealtime());
            ((Chronometer) this.findViewById(R.id.call_duration)).start();
            myWebSocketService.isCallConnected = true;
        }, 100);
    }

    private void updateViews() {
        runOnUiThread(() -> {
            ViewGroup.LayoutParams params = localVideoView.getLayoutParams();
            if (remoteVideoView.getVisibility() == View.VISIBLE) {
                params.height = dpToPx(160);
                params.width = dpToPx(120);
            } else {
                params = new ConstraintLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            }
            localVideoView.setLayoutParams(params);

            ValueAnimator anim = ValueAnimator.ofInt(localVideoView.getMeasuredHeight(), -100);
            ViewGroup.LayoutParams finalParams = params;
            anim.addUpdateListener(valueAnimator -> localVideoView.setLayoutParams(finalParams));
            anim.setDuration(500);
            anim.start();

        });
    }

    /**
     * Util Methods
     */
    public int dpToPx(int dp) {
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        return Math.round(dp * (displayMetrics.xdpi / DisplayMetrics.DENSITY_DEFAULT));
    }

    @SuppressLint("SetTextI18n")
    private void hangup() {

        if (myService != null && myService.isIncomingCall)
            myWebSocketService.setCallStatus("HANGUP");

        findViewById(R.id.end_call).setVisibility(View.GONE);


        ((Chronometer) findViewById(R.id.call_duration)).stop();
        ((TextView) findViewById(R.id.call_status_text)).setText(R.string.hanging_up);

        if (!myWebSocketService.isCallConnected && myWebSocketService.callingUserDetails != null) {
            //TODO - Keval
            ((TextView) findViewById(R.id.call_status_message)).setText("Sorry, " + toTitleCase(myWebSocketService.callingUserDetails.optString("displayName") + " is not available to take your call."));
            findViewById(R.id.call_status_message).setVisibility(View.VISIBLE);
        } else {
            ((TextView) findViewById(R.id.call_status_message)).setText(R.string.access_granted);
        }

        clearSearchBarHandler.removeCallbacks(clearSearchBar);
        clearSearchBarHandler.postDelayed(clearSearchBar, 0);

        runOnUiThread(() -> {
            ViewGroup.LayoutParams params = new ConstraintLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            localVideoView.setLayoutParams(params);
        });
        remoteVideoView.clearImage();
        remoteVideoView.setVisibility(View.INVISIBLE);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (ssTimeout > 0) {
                handler2.postDelayed(r2, ssTimeout * 1000);
                isScreenSaver = true;
            }
            findViewById(R.id.calling_screen_card).setVisibility(View.GONE);
            if (isIntercomEnabled) {
                findViewById(R.id.callingButtons).setVisibility(View.VISIBLE);
            } else {
                userListView.setVisibility(View.VISIBLE);
            }
            materialButtonToggleGroup.check(R.id.guest_btn);
            if(localVideoTrack != null) {
                localVideoTrack.removeSink(localVideoView);
            }

            if (!isSendingSecurityVideo && videoCapturerAndroid != null) {
                try {
                    videoCapturerAndroid.stopCapture();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            localVideoView.clearImage();
            findViewById(R.id.end_call).setVisibility(View.VISIBLE);
            findViewById(R.id.call_duration).setVisibility(View.GONE);
            ((TextView) findViewById(R.id.call_status_text)).setText(R.string.calling);
            ((TextView) findViewById(R.id.username_text)).setText("");
            findViewById(R.id.call_status_message).setVisibility(View.GONE);
            myWebSocketService.isCallConnected = false;
            myWebSocketService.callingUserDetails = null;
        }, 5000);
    }

    public void onPause() {
        super.onPause();

        //pause anything else
    }

    public void restart() {
        System.exit(3);
    }

    private boolean isVSServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    public static class TcpServer extends MainActivity implements Runnable {
        public static final int SERVERPORT = 6123;

        @Override
        public void run() {
            try {
                ServerSocket serverSocket = new ServerSocket();
                serverSocket.setReuseAddress(true);
                serverSocket.bind(new InetSocketAddress(SERVERPORT));
                while (true) {
                    Socket s = serverSocket.accept();
                    Thread t = new Thread(new TcpClientHandler(s));
                    t.setPriority(8);
                    t.start();
                }
            } catch (IOException e) {
                e.printStackTrace();
                Log.e("TCP server has IOException", "error: ", e);
                this.restart();
            }
        }
    }

//    public class updateTask extends TimerTask {
//        @Override
//        public void run() {
//            try {
//                Process process = Runtime.getRuntime().exec("su");
//                DataOutputStream os = new DataOutputStream(process.getOutputStream());
//                os.writeBytes("reboot\n");
//                os.flush();
//            } catch (IOException e) {
//                e.printStackTrace();
//                restart();
//            }
//        }
//
//        private void updateApp() {
//            Intent updateIntent = new Intent(getApplicationContext(), AutoUpdateActivity.class);
//            updateIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//            startActivity(updateIntent);
//            finish();
//        }
//    }
}