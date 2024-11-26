package com.example.smartpropertykiosk;

import static android.view.View.KEEP_SCREEN_ON;
import static android.view.View.SYSTEM_UI_FLAG_FULLSCREEN;
import static android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
import static android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
import static android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
import static android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE;

import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AutoUpdateActivity extends AppCompatActivity {

    // Indicate that we would like to update download progress
    private static final int UPDATE_DOWNLOAD_PROGRESS = 1;
    // Use a background thread to check the progress of downloading
    private final ExecutorService executor = Executors.newFixedThreadPool(1);
    ProgressBar progressBar;
    TextView tv3;
    TextView tv4;
    // Use a hander to update progress bar on the main thread
    private final Handler mainHandler = new Handler(Looper.getMainLooper(), new Handler.Callback() {
        @SuppressLint("SetTextI18n")
        @Override
        public boolean handleMessage(@NonNull Message msg) {
            if (msg.what == UPDATE_DOWNLOAD_PROGRESS) {
                int downloadProgress = msg.arg1;

                // Update your progress bar here.
                runOnUiThread(() -> {
                    if (downloadProgress > 99)
                        tv4.setText("");
                    else tv4.setText(downloadProgress + "%");
                });
                progressBar.setProgress(downloadProgress);
            }
            return true;
        }
    });

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE
                | SYSTEM_UI_FLAG_FULLSCREEN
                | SYSTEM_UI_FLAG_LAYOUT_STABLE
                | SYSTEM_UI_FLAG_IMMERSIVE_STICKY | KEEP_SCREEN_ON
                | SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | SYSTEM_UI_FLAG_HIDE_NAVIGATION);

        setContentView(R.layout.updating_app);
        if(BuildConfig.IS_PRODUCTION) {
            try {
                startLockTask();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        progressBar = findViewById(R.id.progressBar);
        tv3 = findViewById(R.id.text3);
        tv4 = findViewById(R.id.text4);

        //check if file is already exists
        File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/app.apk");
        if (file.exists()) {
            file.delete();
        }

        //prepare download manager
        DownloadManager downloadmanager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
        Uri uri = Uri.parse("https://www.vistadial.com/spcKiosk/app.apk");
        DownloadManager.Request request = new DownloadManager.Request(uri);
        request.setTitle("SmartPropertyKiosk");
        request.setDescription("Downloading");
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "app.apk");
        //start downloading in background
        final long downloadId = downloadmanager.enqueue(request);

        //run task in background for progress
        executor.execute(() -> {
            int progress = 0;
            boolean isDownloadFinished = false;
            while (!isDownloadFinished) {
                Cursor cursor = downloadmanager.query(new DownloadManager.Query().setFilterById(downloadId));
                if (cursor.moveToFirst()) {
                    int downloadStatus = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS));
                    switch (downloadStatus) {
                        case DownloadManager.STATUS_RUNNING:
                            long totalBytes = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                            if (totalBytes > 0) {
                                long downloadedBytes = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                                progress = (int) (downloadedBytes * 100 / totalBytes);
                            }
                            break;
                        case DownloadManager.STATUS_SUCCESSFUL:
                            progress = 100;
                            isDownloadFinished = true;
                            executor.shutdown();
                            mainHandler.removeCallbacksAndMessages(null);
                            runOnUiThread(() -> {
                                progressBar.setIndeterminate(true);
                                tv3.setText("Installing...");
                            });
                            break;
                        case DownloadManager.STATUS_PAUSED:
                        case DownloadManager.STATUS_PENDING:
                            break;
                        case DownloadManager.STATUS_FAILED:
                            isDownloadFinished = true;
                            break;
                    }
                    Message message = Message.obtain();
                    message.what = UPDATE_DOWNLOAD_PROGRESS;
                    message.arg1 = progress;
                    mainHandler.sendMessage(message);
                }
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(BuildConfig.IS_PRODUCTION) {
            try {
                startLockTask();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
