package com.example.smartpropertykiosk;

import org.json.JSONObject;

import java.math.BigInteger;

interface OnJoined {
    void onJoined(com.example.smartpropertykiosk.JanusHandle jh);
}

interface OnRemoteJsep {
    void onRemoteJsep(com.example.smartpropertykiosk.JanusHandle jh, JSONObject jsep);
}

public class JanusHandle {

    public BigInteger handleId;

    public com.example.smartpropertykiosk.OnJoined onJoined;
    public com.example.smartpropertykiosk.OnRemoteJsep onRemoteJsep;
    public com.example.smartpropertykiosk.OnJoined onLeaving;
}
