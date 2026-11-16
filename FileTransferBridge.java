package com.offlinemesh.chat;

import android.app.Activity;
import android.net.Uri;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import org.json.JSONObject;

public final class FileTransferBridge {

    private final Activity activity;
    private final WebView webView;
    private final FileTransferManager fileManager;

    public FileTransferBridge(
            Activity activity,
            WebView webView,
            FileTransferManager fileManager
    ) {
        if (activity == null ||
                webView == null ||
                fileManager == null) {
            throw new IllegalArgumentException(
                    "Bridge parameters cannot be null"
            );
        }

        this.activity = activity;
        this.webView = webView;
        this.fileManager = fileManager;
    }

    @JavascriptInterface
    public void sendFile(String host, String uriString) {
        if (host == null || host.trim().isEmpty() ||
                uriString == null || uriString.trim().isEmpty()) {
            sendError("Host or file URI is missing.");
            return;
        }

        try {
            Uri uri = Uri.parse(uriString);

            fileManager.sendFile(
                    host.trim(),
                    uri,
                    new FileTransferManager.Callback() {

                        @Override
                        public void onProgress(
                                long transferred,
                                long total
                        ) {
                            JSONObject data = new JSONObject();

                            try {
                                data.put("transferred", transferred);
                                data.put("total", total);
                            } catch (Exception ignored) {
                            }

                            callJavaScript(
                                    "onFileTransferProgress",
                                    data
                            );
                        }

                        @Override
                        public void onSuccess(java.io.File file) {
                            JSONObject data = new JSONObject();

                            try {
                                data.put("success", true);
                                data.put("message", "File sent successfully");
                            } catch (Exception ignored) {
                            }

                            callJavaScript(
                                    "onFileTransferSuccess",
                                    data
                            );
                        }

                        @Override
                        public void onError(String error) {
                            sendError(error);
                        }
                    }
            );

        } catch (Exception e) {
            sendError(e.getMessage());
        }
    }

    @JavascriptInterface
    public void receiveFile() {
        fileManager.receiveFile(
                new FileTransferManager.Callback() {

                    @Override
                    public void onProgress(
                            long transferred,
                            long total
                    ) {
                        JSONObject data = new JSONObject();

                        try {
                            data.put("transferred", transferred);
                            data.put("total", total);
                        } catch (Exception ignored) {
                        }

                        callJavaScript(
                                "onFileTransferProgress",
                                data
                        );
                    }

                    @Override
                    public void onSuccess(java.io.File file) {
                        JSONObject data = new JSONObject();

                        try {
                            data.put("success", true);
                            data.put(
                                    "filePath",
                                    file == null ? "" : file.getAbsolutePath()
                            );
                            data.put(
                                    "fileName",
                                    file == null ? "" : file.getName()
                            );
                        } catch (Exception ignored) {
                        }

                        callJavaScript(
                                "onFileTransferReceived",
                                data
                        );
                    }

                    @Override
                    public void onError(String error) {
                        sendError(error);
                    }
                }
        );
    }

    @JavascriptInterface
    public String getBridgeStatus() {
        return "FILE_TRANSFER_BRIDGE_READY";
    }

    private void sendError(String error) {
        JSONObject data = new JSONObject();

        try {
            data.put(
                    "error",
                    error == null ? "Unknown error" : error
            );
        } catch (Exception ignored) {
        }

        callJavaScript("onFileTransferError", data);
    }

    private void callJavaScript(
            String function,
            JSONObject data
    ) {
        activity.runOnUiThread(() -> {
            if (activity.isFinishing() || activity.isDestroyed()) {
                return;
            }

            webView.evaluateJavascript(
                    "if(typeof window." + function +
                    " === 'function'){" +
                    "window." + function + "(" +
                    data.toString() + ");}",
                    null
            );
        });
    }
}
