package com.offlinemesh.chat;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.p2p.WifiP2pManager;

public class MeshBroadcastReceiver extends BroadcastReceiver {

    public interface Listener {
        void onWifiStateChanged(boolean enabled);
        void onPeersChanged();
        void onConnectionChanged();
        void onDeviceChanged();
    }

    private final Listener listener;

    public MeshBroadcastReceiver(Listener listener) {
        this.listener = listener;
    }

    @Override
    public void onReceive(Context context, Intent intent) {

        if (intent == null || listener == null) {
            return;
        }

        String action = intent.getAction();

        if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {

            int state = intent.getIntExtra(
                    WifiP2pManager.EXTRA_WIFI_STATE,
                    -1
            );

            listener.onWifiStateChanged(
                    state == WifiP2pManager.WIFI_P2P_STATE_ENABLED
            );

        } else if (
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)
        ) {

            listener.onPeersChanged();

        } else if (
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)
        ) {

            listener.onConnectionChanged();

        } else if (
                WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action)
        ) {

            listener.onDeviceChanged();
        }
    }
}
