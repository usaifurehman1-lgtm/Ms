package com.offlinemesh.chat;

import android.app.Activity;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import org.json.JSONObject;

public final class P2PMeshBridge
        implements P2PMeshNode.Listener {

    private final Activity activity;
    private final WebView webView;

    private P2PMeshNode meshNode;

    private volatile boolean closed = false;

    public P2PMeshBridge(
            Activity activity,
            WebView webView
    ) {
        this.activity = activity;
        this.webView = webView;
    }

    // =================================
    // INITIALIZE MESH NODE
    // =================================

    public synchronized void initialize(
            android.net.wifi.p2p.WifiP2pManager manager,
            android.net.wifi.p2p.WifiP2pManager.Channel channel
    ) {

        if (closed || meshNode != null) {
            return;
        }

        meshNode = new P2PMeshNode(
                activity,
                manager,
                channel,
                this
        );

        sendEvent("meshInitialized", "Mesh initialized");
    }

    // =================================
    // CONNECTION INFO
    // =================================

    public void onConnectionInfoAvailable(
            android.net.wifi.p2p.WifiP2pInfo info
    ) {

        if (closed || meshNode == null) {
            return;
        }

        meshNode.onConnectionInfoAvailable(info);
    }

    // =================================
    // SEND TEXT MESSAGE
    // =================================

    @JavascriptInterface
    public boolean sendText(String message) {

        if (closed || meshNode == null) {
            sendEvent("error", "Mesh is not initialized");
            return false;
        }

        if (message == null || message.trim().isEmpty()) {
            sendEvent("error", "Message is empty");
            return false;
        }

        boolean sent = meshNode.sendText(message);

        if (sent) {
            sendEvent("messageSent", "Message sent");
        } else {
            sendEvent("error", "No connected peers");
        }

        return sent;
    }

    // =================================
    // STATUS CALLBACK
    // =================================

    @Override
    public void onStatus(String text) {

        sendEvent("meshStatus", text);
    }

    // =================================
    // MESSAGE CALLBACK
    // =================================

    @Override
    public void onMessage(
            String sender,
            String message
    ) {

        JSONObject data = new JSONObject();

        try {
            data.put("sender", sender);
            data.put("message", message);
        } catch (Exception ignored) {
        }

        sendJsonEvent("messageReceived", data);
    }

    // =================================
    // SEND EVENT TO HTML
    // =================================

    private void sendEvent(
            String event,
            String message
    ) {

        if (closed) return;

        String script =
                "window.onP2PMeshEvent && " +
                "window.onP2PMeshEvent(" +
                JSONObject.quote(event) + "," +
                JSONObject.quote(
                        message == null ? "" : message
                ) +
                ");";

        activity.runOnUiThread(() ->
                webView.evaluateJavascript(script, null)
        );
    }

    // =================================
    // SEND JSON EVENT
    // =================================

    private void sendJsonEvent(
            String event,
            JSONObject data
    ) {

        if (closed) return;

        String script =
                "window.onP2PMeshEvent && " +
                "window.onP2PMeshEvent(" +
                JSONObject.quote(event) + "," +
                data.toString() +
                ");";

        activity.runOnUiThread(() ->
                webView.evaluateJavascript(script, null)
        );
    }

    // =================================
    // RELEASE
    // =================================

    public synchronized void release() {

        if (closed) return;

        closed = true;

        if (meshNode != null) {
            meshNode.close();
            meshNode = null;
        }
    }
}
