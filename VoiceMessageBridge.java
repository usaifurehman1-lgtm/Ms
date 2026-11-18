package com.offlinemesh.chat;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import java.io.File;

public class VoiceMessageBridge {

    private final Activity activity;
    private final WebView webView;
    private final VoiceMessageManager voiceManager;

    public VoiceMessageBridge(
            Activity activity,
            WebView webView
    ) {
        this.activity = activity;
        this.webView = webView;
        this.voiceManager = new VoiceMessageManager(activity);
    }

    // HTML से रिकॉर्डिंग शुरू करना
    @JavascriptInterface
    public void startRecording() {

        activity.runOnUiThread(() -> {

            if (activity.checkSelfPermission(
                    Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED) {

                activity.requestPermissions(
                        new String[]{
                                Manifest.permission.RECORD_AUDIO
                        },
                        7001
                );

                sendError("Microphone permission required");
                return;
            }

            voiceManager.startRecording(
                    new VoiceMessageManager.Callback() {

                        @Override
                        public void onStarted() {
                            sendEvent("recordingStarted", "");
                        }

                        @Override
                        public void onCompleted(File file) {
                            sendEvent(
                                    "recordingCompleted",
                                    file.getAbsolutePath()
                            );
                        }

                        @Override
                        public void onError(String error) {
                            sendError(error);
                        }
                    }
            );
        });
    }

    // HTML से रिकॉर्डिंग बंद करना
    @JavascriptInterface
    public void stopRecording() {

        activity.runOnUiThread(() -> {

            voiceManager.stopRecording(
                    new VoiceMessageManager.Callback() {

                        @Override
                        public void onStarted() {
                        }

                        @Override
                        public void onCompleted(File file) {
                            sendEvent(
                                    "recordingCompleted",
                                    file.getAbsolutePath()
                            );
                        }

                        @Override
                        public void onError(String error) {
                            sendError(error);
                        }
                    }
            );
        });
    }

    // सेव की हुई आवाज चलाना
    @JavascriptInterface
    public void playRecording(String filePath) {

        activity.runOnUiThread(() -> {

            if (filePath == null || filePath.trim().isEmpty()) {
                sendError("Audio path is empty");
                return;
            }

            File file = new File(filePath);

            voiceManager.playRecording(
                    file,
                    new VoiceMessageManager.Callback() {

                        @Override
                        public void onStarted() {
                        }

                        @Override
                        public void onCompleted(File file) {
                        }

                        @Override
                        public void onError(String error) {
                            sendError(error);
                        }
                    }
            );

            sendEvent("playbackStarted", filePath);
        });
    }

    // HTML से आवाज बंद करना
    @JavascriptInterface
    public void stopPlayback() {

        activity.runOnUiThread(() -> {
            voiceManager.stopPlayback();
            sendEvent("playbackStopped", "");
        });
    }

    // JavaScript में इवेंट भेजना
    private void sendEvent(String event, String data) {

        String safeData = data == null
                ? ""
                : data.replace("\\", "\\\\")
                      .replace("'", "\\'")
                      .replace("\n", "\\n")
                      .replace("\r", "");

        String script =
                "window.onVoiceBridgeEvent && " +
                "window.onVoiceBridgeEvent('" +
                event + "','" + safeData + "');";

        webView.post(() ->
                webView.evaluateJavascript(script, null)
        );
    }

    // HTML में Error भेजना
    private void sendError(String error) {

        sendEvent("error", error);
    }

    // Bridge बंद करना
    public void release() {

        voiceManager.release();
    }
}
