package com.offlinemesh.chat;

import android.Manifest;
import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.wifi.p2p.*;
import android.view.View;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity {

    private WifiP2pManager manager;
    private WifiP2pManager.Channel channel;
    private BroadcastReceiver receiver;
    private IntentFilter filter;

    private P2PMeshNode meshNode;

    private TextView status;
    private TextView messages;
    private EditText input;
    private ListView peerList;

    private final List<WifiP2pDevice> devices = new ArrayList<>();
    private ArrayAdapter<String> adapter;

    private WifiP2pDevice selectedDevice;

    private static final int PERMISSION_REQUEST = 100;

    private boolean receiverRegistered = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        createInterface();

        manager = (WifiP2pManager)
                getSystemService(Context.WIFI_P2P_SERVICE);

        if (manager == null) {
            status.setText("Wi-Fi Direct उपलब्ध नहीं है");
            return;
        }

        channel = manager.initialize(
                this,
                getMainLooper(),
                () -> status.setText("Wi-Fi Direct channel disconnected")
        );

        meshNode = new P2PMeshNode(this, manager, channel,
                new P2PMeshNode.Listener() {

                    @Override
                    public void onStatus(String text) {
                        runOnUiThread(() -> status.setText(text));
                    }

                    @Override
                    public void onMessage(String sender, String message) {
                        runOnUiThread(() -> messages.append(
                                "\n" + sender + ": " + message
                        ));
                    }
                });

        setupReceiver();
        requestAppPermissions();
    }

    private void createInterface() {

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(16, 16, 16, 16);

        root.setBackgroundColor(0xFFE9EDEF);

        status = new TextView(this);
        status.setText("Offline Mesh Chat");
        status.setTextSize(18);
        status.setPadding(8, 12, 8, 12);

        Button discover = new Button(this);
        discover.setText("Find Nearby Devices");

        Button createGroup = new Button(this);
        createGroup.setText("Create Wi-Fi Group");

        Button connect = new Button(this);
        connect.setText("Connect Selected Device");

        peerList = new ListView(this);

        adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                new ArrayList<>()
        );

        peerList.setAdapter(adapter);

        peerList.setOnItemClickListener((parent, view, position, id) -> {
            if (position < devices.size()) {
                selectedDevice = devices.get(position);

                status.setText(
                        "Selected: " + selectedDevice.deviceName
                );
            }
        });

        messages = new TextView(this);
        messages.setText("Messages will appear here.");
        messages.setTextSize(15);
        messages.setPadding(8, 12, 8, 12);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(messages);

        input = new EditText(this);
        input.setHint("Write a message");

        Button send = new Button(this);
        send.setText("Send Message");

        discover.setOnClickListener(v -> discoverPeers());

        createGroup.setOnClickListener(v -> createGroup());

        connect.setOnClickListener(v -> connectSelected());

        send.setOnClickListener(v -> sendMessage());

        root.addView(status);
        root.addView(discover);
        root.addView(createGroup);
        root.addView(connect);
        root.addView(peerList);

        root.addView(scroll,
                new LinearLayout.LayoutParams(
                        -1, 0, 1
                ));

        root.addView(input);
        root.addView(send);

        setContentView(root);
    }

    private void setupReceiver() {

        filter = new IntentFilter();

        filter.addAction(
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION
        );

        filter.addAction(
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION
        );

        filter.addAction(
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION
        );

        filter.addAction(
                WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION
        );

        receiver = new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {

                String action = intent.getAction();

                if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION
                        .equals(action)) {

                    requestPeers();
                }

                if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION
                        .equals(action)) {

                    requestConnectionInfo();
                }
            }
        };
    }

    private void registerP2PReceiver() {

        if (receiverRegistered) return;

        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(
                    receiver,
                    filter,
                    Context.RECEIVER_EXPORTED
            );
        } else {
            registerReceiver(receiver, filter);
        }

        receiverRegistered = true;
    }

    private void requestAppPermissions() {

        List<String> required = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= 33) {

            if (checkSelfPermission(
                    Manifest.permission.NEARBY_WIFI_DEVICES
            ) != PackageManager.PERMISSION_GRANTED) {

                required.add(
                        Manifest.permission.NEARBY_WIFI_DEVICES
                );
            }

        } else if (Build.VERSION.SDK_INT >= 23) {

            if (checkSelfPermission(
                    Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED) {

                required.add(
                        Manifest.permission.ACCESS_FINE_LOCATION
                );
            }
        }

        if (Build.VERSION.SDK_INT >= 23) {

            if (checkSelfPermission(
                    Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED) {

                required.add(Manifest.permission.RECORD_AUDIO);
            }

            if (checkSelfPermission(
                    Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED) {

                required.add(Manifest.permission.CAMERA);
            }
        }

        if (!required.isEmpty()) {

            requestPermissions(
                    required.toArray(new String[0]),
                    PERMISSION_REQUEST
            );

        } else {

            registerP2PReceiver();
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults
    ) {
        super.onRequestPermissionsResult(
                requestCode, permissions, grantResults
        );

        if (requestCode == PERMISSION_REQUEST) {

            boolean granted = true;

            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    granted = false;
                    break;
                }
            }

            if (granted) {
                registerP2PReceiver();
                status.setText("Permissions granted");
            } else {
                status.setText("Required permissions denied");
            }
        }
    }

    private void discoverPeers() {

        if (!receiverRegistered) {
            status.setText("Grant permissions first");
            return;
        }

        status.setText("Searching nearby devices...");

        manager.discoverPeers(channel,
                new WifiP2pManager.ActionListener() {

                    @Override
                    public void onSuccess() {
                        status.setText("Discovery started");
                    }

                    @Override
                    public void onFailure(int reason) {
                        status.setText(
                                "Discovery failed: " + reason
                        );
                    }
                });
    }

    private void requestPeers() {

        manager.requestPeers(channel, peerList -> {

            devices.clear();
            devices.addAll(peerList.getDeviceList());

            List<String> names = new ArrayList<>();

            for (WifiP2pDevice device : devices) {

                names.add(
                        device.deviceName + "\n" +
                        device.deviceAddress + "\n" +
                        device.status
                );
            }

            adapter.clear();
            adapter.addAll(names);
            adapter.notifyDataSetChanged();

            status.setText(
                    "Devices found: " + devices.size()
            );
        });
    }

    private void createGroup() {

        manager.createGroup(channel,
                new WifiP2pManager.ActionListener() {

                    @Override
                    public void onSuccess() {
                        status.setText("Creating Wi-Fi Direct group...");
                    }

                    @Override
                    public void onFailure(int reason) {
                        status.setText(
                                "Group creation failed: " + reason
                        );
                    }
                });
    }

    private void connectSelected() {

        if (selectedDevice == null) {
            status.setText("Select a device first");
            return;
        }

        WifiP2pConfig config = new WifiP2pConfig();

        config.deviceAddress = selectedDevice.deviceAddress;

        manager.connect(channel, config,
                new WifiP2pManager.ActionListener() {

                    @Override
                    public void onSuccess() {
                        status.setText("Connection requested...");
                    }

                    @Override
                    public void onFailure(int reason) {
                        status.setText(
                                "Connection failed: " + reason
                        );
                    }
                });
    }

    private void requestConnectionInfo() {

        manager.requestConnectionInfo(
                channel,
                info -> {

                    if (info != null && info.groupFormed) {

                        status.setText(
                                info.isGroupOwner
                                        ? "Connected: This phone is Group Owner"
                                        : "Connected: Group Client"
                        );

                        meshNode.onConnectionInfoAvailable(info);
                    }
                }
        );
    }

    private void sendMessage() {

        String text = input.getText().toString().trim();

        if (text.isEmpty()) return;

        if (meshNode.sendText(text)) {

            messages.append("\nMe: " + text);
            input.setText("");

        } else {

            status.setText("No connected device");
        }
    }

    @Override
    protected void onDestroy() {

        if (receiverRegistered) {
            try {
                unregisterReceiver(receiver);
            } catch (Exception ignored) {
            }

            receiverRegistered = false;
        }

        if (meshNode != null) {
            meshNode.close();
        }

        super.onDestroy();
    }
}
