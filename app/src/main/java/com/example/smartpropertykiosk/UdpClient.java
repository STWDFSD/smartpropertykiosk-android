package com.example.smartpropertykiosk;

import static android.content.Context.MODE_PRIVATE;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicBoolean;

public class UdpClient implements Runnable {
    private final Context context;
    SharedPreferences sp;

    public UdpClient(Context context) {
        this.context = context;
    }

    public static void sendBroardcast() {

        AsyncTask<Void, Void, Void> asyncClient = new AsyncTask<>() {
            @Override
            protected Void doInBackground(Void... params) {
                try (DatagramSocket datagramSocket = new DatagramSocket()) {
                    JSONObject msgObj = new JSONObject();
                    msgObj.put("udpCommand", "poll");
                    String message = msgObj.toString();
                    DatagramPacket packet = new DatagramPacket(message.getBytes(), message.length(), InetAddress.getByName("255.255.255.255"), 38000);
                    datagramSocket.setBroadcast(true);
                    datagramSocket.send(packet);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return null;
            }
        };

        asyncClient.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    @Override
    public void run() {
        sp = context.getSharedPreferences("pcplus", MODE_PRIVATE);
        ArrayList<String> groupArray = new ArrayList<String>();
        AtomicBoolean run = new AtomicBoolean(true);
        sendBroardcast();

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            run.set(false);
            sp.edit().putStringSet("groupArray", new HashSet<>(groupArray)).apply();
        }, 2000);

        groupArray.add("ac00001");

        try (DatagramSocket udpSocket = new DatagramSocket(null)) {
            udpSocket.setReuseAddress(true);
            udpSocket.bind(new InetSocketAddress(38000));
            byte[] buffer = new byte[8000];

            while (run.get()) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                udpSocket.receive(packet);
                String text = new String(buffer, 0, packet.getLength());
                JSONObject objText = new JSONObject(text);
                if (objText.has("group")) {
                    String group = objText.optString("group");
                    if (!groupArray.contains(group)) {
                        groupArray.add(group);
                    }
                }
            }
        } catch (SocketException e) {
            Log.e("UDP Socket Exception", "error: ", e);
        } catch (IOException e) {
            Log.e("UDP IO Exception", "error: ", e);
        } catch (JSONException e) {
            Log.e("JSON Exception", "error: ", e);
        }
    }
}