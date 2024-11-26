package com.example.smartpropertykiosk;

import static com.example.smartpropertykiosk.MainActivity.input;
import static com.example.smartpropertykiosk.MainActivity.output;
import static java.lang.Thread.sleep;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;

public class TcpClientHandler implements Runnable {

    private final Socket s;
    private final BufferedReader in;
    private final Handler statusHandler = new Handler(Looper.getMainLooper());
    boolean run = true;
    private int resultInput1;
    private int resultInput2;

    public TcpClientHandler(Socket clientSocket) throws IOException {
        this.s = clientSocket;
        in = new BufferedReader(new InputStreamReader(s.getInputStream()));
    }

    @Override
    public void run() {
        while (run) {
            try {
                String incomingMsg = in.readLine() + System.getProperty("line.separator");
                String msg = incomingMsg.trim();
                String[] tempArray = msg.split(" ");
                if (tempArray.length > 0)
                    handleTcpMsg(tempArray);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void handleTcpMsg(String[] msgArray) throws IOException {
        switch (msgArray[0]) {
            case "R": {
                if (msgArray.length > 1) {
                    try {
                        int relay = Integer.parseInt(msgArray[1]);
                        if (msgArray.length > 2) {
                            long time = Long.parseLong(msgArray[2]);
                            openRelay(relay, time);
                        } else {
                            openRelay(relay, 5000);
                        }

                    } catch (Exception e) {
                        replyToClient("Error in command");
                    }

                } else openRelay(0, 5000);
                break;
            }
            case "G": {
                //TODO
                if (msgArray.length > 1 && msgArray[1].contains(":")) {
                    Thread t = new Thread(() -> {
                        try {
                            Process process = Runtime.getRuntime().exec("su");
                            DataOutputStream os = new DataOutputStream(process.getOutputStream());
                            os.writeBytes("echo \"" + msgArray[1] + "\" > /sys/kernel/wiegand/wiegand" + msgArray[2] + "\n");
                            os.flush();
                            sleep(2000);
                            process.destroy();
                            Log.e("wiegand write has IOException", "error: NULL");
                        } catch (Exception e) {
                            Log.e("wiegand write has IOException", "error: ", e);
                            e.printStackTrace();
                        }
                    });
                    t.start();
                }
                break;
            }
            case "S": {
                statusHandler.removeCallbacks(oneRunable);
                resultInput1 = -1;
                resultInput2 = -1;
                statusHandler.postDelayed(oneRunable, 0);
                break;
            }
            case "null": {
                in.close();
                statusHandler.removeCallbacks(oneRunable);
                s.close();
                run = false;
            }
        }
    }

    public void openRelay(int relay, long time) {
        if (relay == 0) {
            output(3);
            replyToClient("SUCCESS");
            new Handler(Looper.getMainLooper()).postDelayed(() -> output(4), time);
        } else if (relay == 1) {
            output(5);
            replyToClient("SUCCESS");
            new Handler(Looper.getMainLooper()).postDelayed(() -> output(6), time);
        } else replyToClient("Relay not found");
    }

    private void replyToClient(String msg) {
        BufferedWriter out;
        try {
            out = new BufferedWriter(new OutputStreamWriter(s.getOutputStream()));
            String outgoingMsg = msg + System.getProperty("line.separator");
            out.write(outgoingMsg);
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Runnable oneRunable = new Runnable() {
        @Override
        public void run() {
            int result1 = input(1);
            int result2 = input(2);
            if (result1 != resultInput1 || result2 != resultInput2) {
                resultInput1 = result1;
                resultInput2 = result2;
                Thread thread = new Thread(() -> {
                    replyToClient("INPUT 1 " + resultInput1);
                    replyToClient("INPUT 2 " + resultInput2);
                });
                thread.start();
            }
            statusHandler.postDelayed(oneRunable, 500);
        }
    };
}
