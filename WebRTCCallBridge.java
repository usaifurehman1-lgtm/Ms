package com.offlinemesh.chat;

import android.app.Activity;
import android.webkit.JavascriptInterface;

import org.json.JSONObject;
import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoTrack;

public class WebRTCCallBridge {

    public interface BridgeListener {
        void onEvent(String event, JSONObject data);
    }

    private final Activity activity;
    private final WebRTCCallManager callManager;
    private final BridgeListener listener;

    private boolean callActive = false;

    public WebRTCCallBridge(
            Activity activity,
            WebRTCCallManager callManager,
            BridgeListener listener
    ) {
        this.activity = activity;
        this.callManager = callManager;
        this.listener = listener;
    }

    // Initialize WebRTC video renderers
    @JavascriptInterface
    public void initialize(
            SurfaceViewRenderer localRenderer,
            SurfaceViewRenderer remoteRenderer
    ) {
        activity.runOnUiThread(() -> {
            callManager.initialize(
                    localRenderer,
                    remoteRenderer
            );

            sendEvent("initialized", "WebRTC initialized");
        });
    }

    // Create audio/video media
    @JavascriptInterface
    public void createMedia() {
        activity.runOnUiThread(() -> {
            callManager.createMedia();
            sendEvent("media_ready", "Media created");
        });
    }

    // Create peer connection
    @JavascriptInterface
    public void createPeer(boolean audioOnly) {
        activity.runOnUiThread(() -> {
            callManager.createPeer(audioOnly);
            callActive = true;

            sendEvent(
                    "peer_created",
                    audioOnly ? "Audio call" : "Video call"
            );
        });
    }

    // Start outgoing call
    @JavascriptInterface
    public void startCall(boolean audioOnly) {
        activity.runOnUiThread(() -> {
            callManager.createMedia();
            callManager.createPeer(audioOnly);
            callManager.createOffer();

            callActive = true;

            sendEvent(
                    "call_started",
                    audioOnly ? "Audio call started" : "Video call started"
            );
        });
    }

    // Accept incoming call manually by user
    @JavascriptInterface
    public void answerCall() {
        activity.runOnUiThread(() -> {
            callManager.createAnswer();
            callActive = true;

            sendEvent("call_answered", "Call accepted");
        });
    }

    // Receive SDP offer/answer
    @JavascriptInterface
    public void setRemoteDescription(
            String type,
            String sdp
    ) {
        activity.runOnUiThread(() -> {
            try {
                SessionDescription.Type descriptionType =
                        SessionDescription.Type.fromCanonicalForm(type);

                SessionDescription description =
                        new SessionDescription(
                                descriptionType,
                                sdp
                        );

                callManager.setRemoteDescription(description);

                sendEvent(
                        "remote_description",
                        "Remote description received"
                );

            } catch (Exception e) {
                sendEvent("error", e.getMessage());
            }
        });
    }

    // Receive ICE candidate
    @JavascriptInterface
    public void addIceCandidate(
            String sdp,
            String mid,
            int mLineIndex
    ) {
        activity.runOnUiThread(() -> {
            try {
                IceCandidate candidate =
                        new IceCandidate(
                                mid,
                                mLineIndex,
                                sdp
                        );

                callManager.addIceCandidate(candidate);

                sendEvent(
                        "ice_candidate",
                        "ICE candidate added"
                );

            } catch (Exception e) {
                sendEvent("error", e.getMessage());
            }
        });
    }

    // Microphone control
    @JavascriptInterface
    public void setMicrophoneEnabled(boolean enabled) {
        activity.runOnUiThread(() -> {
            callManager.setMicrophoneEnabled(enabled);

            sendEvent(
                    "microphone",
                    enabled ? "Microphone ON" : "Microphone OFF"
            );
        });
    }

    // Camera control
    @JavascriptInterface
    public void setCameraEnabled(boolean enabled) {
        activity.runOnUiThread(() -> {
            callManager.setCameraEnabled(enabled);

            sendEvent(
                    "camera",
                    enabled ? "Camera ON" : "Camera OFF"
            );
        });
    }

    // Switch camera
    @JavascriptInterface
    public void switchCamera() {
        activity.runOnUiThread(() -> {
            callManager.switchCamera();
            sendEvent("camera_switch", "Camera switched");
        });
    }

    // Reject/end call
    @JavascriptInterface
    public void endCall() {
        activity.runOnUiThread(() -> {
            callManager.closeCall();
            callActive = false;

            sendEvent("call_ended", "Call ended");
        });
    }

    @JavascriptInterface
    public boolean isCallActive() {
        return callActive;
    }

    // Send event to HTML
    private void sendEvent(
            String event,
            String message
    ) {
        if (listener == null) return;

        try {
            JSONObject data = new JSONObject();
            data.put("message", message);

            listener.onEvent(event, data);

        } catch (Exception ignored) {
        }
    }
}
