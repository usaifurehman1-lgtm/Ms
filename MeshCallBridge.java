package com.offlinemesh.chat;

import android.app.Activity;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import org.json.JSONObject;
import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;

public final class MeshCallBridge {

    private final Activity activity;
    private final WebView webView;

    private final MeshCallController controller;

    private volatile boolean closed = false;

    public MeshCallBridge(
            Activity activity,
            WebView webView
    ) {
        this.activity = activity;
        this.webView = webView;

        controller = new MeshCallController(
                new MeshCallController.CallListener() {

                    @Override
                    public void onOffer(SessionDescription offer) {
                        sendEvent(
                                "offerReceived",
                                offer.description
                        );
                    }

                    @Override
                    public void onAnswer(SessionDescription answer) {
                        sendEvent(
                                "answerReceived",
                                answer.description
                        );
                    }

                    @Override
                    public void onIceCandidate(IceCandidate candidate) {

                        JSONObject data = new JSONObject();

                        try {
                            data.put("sdpMid", candidate.sdpMid);
                            data.put(
                                    "sdpMLineIndex",
                                    candidate.sdpMLineIndex
                            );
                            data.put("candidate", candidate.sdp);

                        } catch (Exception e) {
                            sendEvent("error", e.getMessage());
                            return;
                        }

                        sendJsonEvent("iceCandidateReceived", data);
                    }

                    @Override
                    public void onConnected() {
                        sendEvent("signalConnected", "Connected");
                    }

                    @Override
                    public void onDisconnected() {
                        sendEvent("signalDisconnected", "Disconnected");
                    }

                    @Override
                    public void onError(String message) {
                        sendEvent("error", message);
                    }
                }
        );
    }

    // =================================
    // START SIGNALING SERVER
    // =================================

    @JavascriptInterface
    public void startWaiting(int port) {

        if (closed) return;

        if (port < 1 || port > 65535) {
            sendEvent("error", "Invalid port");
            return;
        }

        controller.startWaiting(port);

        sendEvent("serverStarted", String.valueOf(port));
    }

    // =================================
    // CONNECT TO PEER
    // =================================

    @JavascriptInterface
    public void connectToPeer(String host, int port) {

        if (closed) return;

        if (host == null || host.trim().isEmpty()) {
            sendEvent("error", "Invalid peer address");
            return;
        }

        if (port < 1 || port > 65535) {
            sendEvent("error", "Invalid port");
            return;
        }

        controller.connectToPeer(host.trim(), port);

        sendEvent("connecting", host);
    }

    // =================================
    // SEND OFFER
    // =================================

    @JavascriptInterface
    public void sendOffer(String sdp) {

        if (closed) return;

        if (sdp == null || sdp.trim().isEmpty()) {
            sendEvent("error", "Invalid offer SDP");
            return;
        }

        SessionDescription offer =
                new SessionDescription(
                        SessionDescription.Type.OFFER,
                        sdp
                );

        controller.sendOffer(offer);

        sendEvent("offerSent", "Offer sent");
    }

    // =================================
    // SEND ANSWER
    // =================================

    @JavascriptInterface
    public void sendAnswer(String sdp) {

        if (closed) return;

        if (sdp == null || sdp.trim().isEmpty()) {
            sendEvent("error", "Invalid answer SDP");
            return;
        }

        SessionDescription answer =
                new SessionDescription(
                        SessionDescription.Type.ANSWER,
                        sdp
                );

        controller.sendAnswer(answer);

        sendEvent("answerSent", "Answer sent");
    }

    // =================================
    // SEND ICE CANDIDATE
    // =================================

    @JavascriptInterface
    public void sendIceCandidate(
            String sdpMid,
            int sdpMLineIndex,
            String candidate
    ) {

        if (closed) return;

        if (candidate == null || candidate.trim().isEmpty()) {
            sendEvent("error", "Invalid ICE candidate");
            return;
        }

        IceCandidate iceCandidate =
                new IceCandidate(
                        sdpMid,
                        sdpMLineIndex,
                        candidate
                );

        controller.sendIceCandidate(iceCandidate);

        sendEvent("iceCandidateSent", "ICE candidate sent");
    }

    // =================================
    // SEND JSON TO HTML
    // =================================

    private void sendJsonEvent(
            String event,
            JSONObject data
    ) {

        if (closed) return;

        String script =
                "window.onMeshCallEvent && " +
                "window.onMeshCallEvent(" +
                JSONObject.quote(event) + "," +
                data.toString() +
                ");";

        webView.post(() ->
                webView.evaluateJavascript(script, null)
        );
    }

    // =================================
    // SEND TEXT EVENT TO HTML
    // =================================

    private void sendEvent(
            String event,
            String message
    ) {

        if (closed) return;

        String script =
                "window.onMeshCallEvent && " +
                "window.onMeshCallEvent(" +
                JSONObject.quote(event) + "," +
                JSONObject.quote(
                        message == null ? "" : message
                ) +
                ");";

        webView.post(() ->
                webView.evaluateJavascript(script, null)
        );
    }

    // =================================
    // CLOSE CONNECTION
    // =================================

    public void close() {

        if (closed) return;

        closed = true;

        controller.close();
    }
}
