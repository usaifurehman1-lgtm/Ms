package com.offlinemesh.chat;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;

import java.util.ArrayList;
import java.util.List;

public class MeshPermissionManager {

    public static final int REQUEST_CODE = 2001;

    public interface PermissionCallback {
        void onGranted();
        void onDenied();
    }

    public static void request(
            Activity activity,
            PermissionCallback callback
    ) {

        List<String> permissions = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= 33) {

            permissions.add(
                    Manifest.permission.NEARBY_WIFI_DEVICES
            );

        } else if (Build.VERSION.SDK_INT >= 23) {

            permissions.add(
                    Manifest.permission.ACCESS_FINE_LOCATION
            );
        }

        if (Build.VERSION.SDK_INT >= 23) {

            permissions.add(Manifest.permission.RECORD_AUDIO);
            permissions.add(Manifest.permission.CAMERA);
        }

        List<String> missing = new ArrayList<>();

        for (String permission : permissions) {

            if (activity.checkSelfPermission(permission)
                    != PackageManager.PERMISSION_GRANTED) {

                missing.add(permission);
            }
        }

        if (missing.isEmpty()) {

            callback.onGranted();

        } else {

            activity.requestPermissions(
                    missing.toArray(new String[0]),
                    REQUEST_CODE
            );
        }
    }

    public static boolean allGranted(
            Activity activity,
            String[] permissions,
            int[] results
    ) {

        if (permissions.length != results.length) {
            return false;
        }

        for (int result : results) {

            if (result != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }

        return true;
    }
}
