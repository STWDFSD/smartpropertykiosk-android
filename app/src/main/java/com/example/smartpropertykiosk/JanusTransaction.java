package com.example.smartpropertykiosk;

import org.json.JSONObject;

interface TransactionCallbackSuccess {
    void success(JSONObject jo);
}

interface TransactionCallbackError {
    void error(JSONObject jo);
}

public class JanusTransaction {
    public String tid;
    public com.example.smartpropertykiosk.TransactionCallbackSuccess success;
    public com.example.smartpropertykiosk.TransactionCallbackError error;
}
