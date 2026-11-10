package com.offlinemesh.chat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;

public class MainActivity extends ComponentActivity {

    private static final int REQUEST_PERMISSIONS = 100;

    private TextView statusText;
    private EditText ipInput;
    private MeshCallController callController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        createInterface();
        requestAppPermissions();
    }

    private void createInterface() {

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 24, 24, 24);

        statusText = new TextView(this);
        statusText.setText("Offline Mesh Chat");
        statusText.setTextSize(20);

        ipInput = new EditText(this);
        ipInput.setHint("Peer IP address");

        Button waitButton = new Button(this);
        waitButton.setText("Wait for peer");

        Button connectButton = new Button(this);
        connectButton.setText("Connect");

        Button endButton = new Button(this);
        endButton.setText("End connection");

        root.addView(statusText);
        root.addView(ipInput);
        root.addView(waitButton);
        root.addView(connectButton);
        root.addView(endButton);

        setContentView(root);

        waitButton.setOnClickListener(v -> startWaiting());

        connectButton.setOnClickListener(v -> connectPeer());

        endButton.setOnClickListener(v -> endConnection());
    }

    private void requestAppPermissions() {

        String[] permissions;

        if (Build.VERSION.SDK_INT >= 33) {
            permissions = new String[]{
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.CAMERA,
                    Manifest.permission.NEARBY_WIFI_DEVICES
            };
        } else {
            permissions = new String[]{
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.CAMERA,
                    Manifest.permission.ACCESS_FINE_LOCATION
            };
        }

        boolean missing = false;

        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                missing = true;
                break;
            }
        }

        if (missing) {
            ActivityCompat.requestPermissions(
                    this,
                    permissions,
                    REQUEST_PERMISSIONS
            );
        }
    }

    private void startWaiting() {

        if (callController != null) {
            callController.close();
        }

        callController = createCallController();

        callController.startWaiting(8991);

        statusText.setText("Waiting for peer on port 8991...");
    }

    private void connectPeer() {

        String ip = ipInput.getText().toString().trim();

        if (ip.isEmpty()) {
            Toast.makeText(
                    this,
                    "Enter peer IP address",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        if (callController != null) {
            callController.close();
        }

        callController = createCallController();

        callController.connectToPeer(ip, 8991);

        statusText.setText("Connecting to " + ip);
    }

    private MeshCallController createCallController() {

        return new MeshCallController(
                new MeshCallController.CallListener() {

                    @Override
                    public void onOffer(SessionDescription offer) {
                        runOnUiThread(() ->
                                statusText.setText("Offer received"));
                    }

                    @Override
                    public void onAnswer(SessionDescription answer) {
                        runOnUiThread(() ->
                                statusText.setText("Answer received"));
                    }

                    @Override
                    public void onIceCandidate(IceCandidate candidate) {
                        // Candidate received; WebRTC integration required.
                    }

                    @Override
                    public void onConnected() {
                        runOnUiThread(() ->
                                statusText.setText("Socket connected"));
                    }

                    @Override
                    public void onDisconnected() {
                        runOnUiThread(() ->
                                statusText.setText("Disconnected"));
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(() ->
                                statusText.setText("Error: " + message));
                    }
                }
        );
    }

    private void endConnection() {

        if (callController != null) {
            callController.close();
            callController = null;
        }

        statusText.setText("Connection ended");
    }

    @Override
    protected void onDestroy() {

        if (callController != null) {
            callController.close();
            callController = null;
        }

        super.onDestroy();
    }
}
