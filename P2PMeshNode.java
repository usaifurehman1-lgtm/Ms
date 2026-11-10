package com.offlinemesh.chat;

import android.content.Context;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class P2PMeshNode {

    private static final int PORT = 8988;

    private final Context context;
    private final WifiP2pManager manager;
    private final WifiP2pManager.Channel channel;
    private final Listener listener;

    private final ExecutorService executor =
            Executors.newCachedThreadPool();

    private final Set<PeerSession> sessions =
            ConcurrentHashMap.newKeySet();

    private final Set<String> receivedPackets =
            ConcurrentHashMap.newKeySet();

    private ServerSocket serverSocket;

    private volatile boolean running = true;

    private String nodeId = UUID.randomUUID().toString();

    public interface Listener {

        void onStatus(String text);

        void onMessage(String sender, String message);
    }

    public P2PMeshNode(
            Context context,
            WifiP2pManager manager,
            WifiP2pManager.Channel channel,
            Listener listener
    ) {

        this.context = context;
        this.manager = manager;
        this.channel = channel;
        this.listener = listener;
    }

    public void onConnectionInfoAvailable(WifiP2pInfo info) {

        if (info == null || !info.groupFormed) {
            return;
        }

        if (info.isGroupOwner) {

            startServer();

        } else {

            if (info.groupOwnerAddress != null) {

                connectToGroupOwner(
                        info.groupOwnerAddress.getHostAddress()
                );
            }
        }
    }

    private synchronized void startServer() {

        if (serverSocket != null || !running) {
            return;
        }

        executor.execute(() -> {

            try {

                serverSocket = new ServerSocket(PORT);

                notifyStatus("Mesh server listening on port " + PORT);

                while (running) {

                    Socket socket = serverSocket.accept();

                    PeerSession session = new PeerSession(socket);

                    sessions.add(session);

                    executor.execute(session);
                }

            } catch (IOException e) {

                if (running) {
                    notifyStatus("Server error: " + e.getMessage());
                }
            }
        });
    }

    private void connectToGroupOwner(String address) {

        executor.execute(() -> {

            try {

                Socket socket = new Socket();

                socket.connect(
                        new InetSocketAddress(address, PORT),
                        15000
                );

                if (!running) {
                    socket.close();
                    return;
                }

                PeerSession session = new PeerSession(socket);

                sessions.add(session);

                executor.execute(session);

                notifyStatus("Connected to group owner");

            } catch (IOException e) {

                notifyStatus(
                        "Connection error: " + e.getMessage()
                );
            }
        });
    }

    public boolean sendText(String text) {

        if (!running || sessions.isEmpty()) {
            return false;
        }

        String packetId = UUID.randomUUID().toString();

        String encoded = Base64.getEncoder().encodeToString(
                text.getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );

        String packet = packetId + "|" +
                nodeId + "|" +
                "8" + "|" +
                encoded;

        receivedPackets.add(packetId);

        broadcast(packet, null);

        return true;
    }

    private void broadcast(
            String packet,
            PeerSession excluded
    ) {

        for (PeerSession session : sessions) {

            if (session != excluded) {
                session.send(packet);
            }
        }
    }

    private void handlePacket(
            String packet,
            PeerSession source
    ) {

        try {

            String[] parts = packet.split("\\|", 4);

            if (parts.length != 4) {
                return;
            }

            String packetId = parts[0];
            String origin = parts[1];

            int ttl = Integer.parseInt(parts[2]);

            String encoded = parts[3];

            if (!receivedPackets.add(packetId)) {
                return;
            }

            byte[] bytes = Base64.getDecoder().decode(encoded);

            String message = new String(
                    bytes,
                    java.nio.charset.StandardCharsets.UTF_8
            );

            listener.onMessage(origin, message);

            if (ttl > 1) {

                String forwarded = packetId + "|" +
                        origin + "|" +
                        (ttl - 1) + "|" +
                        encoded;

                broadcast(forwarded, source);
            }

        } catch (Exception e) {

            notifyStatus("Invalid message packet");
        }
    }

    private void notifyStatus(String text) {

        if (listener != null) {
            listener.onStatus(text);
        }
    }

    private class PeerSession implements Runnable {

        private final Socket socket;

        private DataInputStream input;
        private DataOutputStream output;

        private final Object sendLock = new Object();

        private final AtomicBoolean closed =
                new AtomicBoolean(false);

        PeerSession(Socket socket) {

            this.socket = socket;
        }

        @Override
        public void run() {

            try {

                input = new DataInputStream(
                        new BufferedInputStream(
                                socket.getInputStream()
                        )
                );

                output = new DataOutputStream(
                        new BufferedOutputStream(
                                socket.getOutputStream()
                        )
                );

                notifyStatus("Peer session connected");

                while (running && !closed.get()) {

                    String packet = input.readUTF();

                    handlePacket(packet, this);
                }

            } catch (IOException e) {

                if (running && !closed.get()) {
                    notifyStatus("Peer disconnected");
                }

            } finally {

                close();
            }
        }

        void send(String packet) {

            if (closed.get()) return;

            synchronized (sendLock) {

                try {

                    if (output == null) return;

                    output.writeUTF(packet);
                    output.flush();

                } catch (IOException e) {

                    close();
                }
            }
        }

        void close() {

            if (!closed.compareAndSet(false, true)) {
                return;
            }

            sessions.remove(this);

            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    public void close() {

        running = false;

        for (PeerSession session :
                new ArrayList<>(sessions)) {

            session.close();
        }

        sessions.clear();

        if (serverSocket != null) {

            try {
                serverSocket.close();
            } catch (IOException ignored) {
            }
        }

        executor.shutdownNow();
    }
}
