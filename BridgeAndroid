package com.offlinemesh.chat;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.webkit.JavascriptInterface;
import android.widget.Toast;

import org.json.JSONObject;

public final class AndroidBridge {

    private final Activity activity;
    private final Context context;

    public AndroidBridge(Activity activity) {
        this.activity = activity;
        this.context = activity.getApplicationContext();
    }

    @JavascriptInterface
    public String getBridgeStatus() {
        return "CONNECTED";
    }

    @JavascriptInterface
    public String getDeviceInfo() {
        try {
            JSONObject info = new JSONObject();

            info.put("brand", Build.BRAND);
            info.put("model", Build.MODEL);
            info.put("manufacturer", Build.MANUFACTURER);
            info.put("androidVersion", Build.VERSION.RELEASE);
            info.put("sdk", Build.VERSION.SDK_INT);

            return info.toString();

        } catch (Exception e) {
            return "{}";
        }
    }

    @JavascriptInterface
    public boolean saveData(String key, String value) {
        if (!validKey(key) || value == null) {
            return false;
        }

        return context.getSharedPreferences(
                "HAYE_STORAGE",
                Context.MODE_PRIVATE
        ).edit().putString(key, value).commit();
    }

    @JavascriptInterface
    public String readData(String key) {
        if (!validKey(key)) {
            return "";
        }

        return context.getSharedPreferences(
                "HAYE_STORAGE",
                Context.MODE_PRIVATE
        ).getString(key, "");
    }

    @JavascriptInterface
    public boolean deleteData(String key) {
        if (!validKey(key)) {
            return false;
        }

        return context.getSharedPreferences(
                "HAYE_STORAGE",
                Context.MODE_PRIVATE
        ).edit().remove(key).commit();
    }

    @JavascriptInterface
    public void showToast(String message) {
        activity.runOnUiThread(() ->
                Toast.makeText(
                        activity,
                        message,
                        Toast.LENGTH_SHORT
                ).show()
        );
    }

    @JavascriptInterface
    public void shareText(String message) {
        if (message == null) return;

        activity.runOnUiThread(() -> {
            Intent intent = new Intent(Intent.ACTION_SEND);

            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TEXT, message);

            activity.startActivity(
                    Intent.createChooser(intent, "Share")
            );
        });
    }

    private boolean validKey(String key) {
        return key != null
                && key.matches("[a-zA-Z0-9._-]{1,100}")
                && !key.equals(".")
                && !key.equals("..");
    }
}
