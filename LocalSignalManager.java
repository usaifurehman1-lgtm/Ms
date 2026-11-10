package com.offlinemesh.chat;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class LocalSignalManager {

    private final ExecutorService executor =
            Executors.newCachedThreadPool();

    private final AtomicBoolean running =
            new AtomicBoolean(true);

    private final Object sendLock = new Object();

    private ServerSocket serverSocket;
    private Socket socket;

    private BufferedReader reader;
    private BufferedWriter writer;

    public interface Listener {
        void onSignal(JSONObject signal);
        void onConnected();
        void onDisconnected();
        void onError(String error);
    }

    private final Listener listener;

    public LocalSignalManager(Listener listener) {
        this.listener = listener;
    }

    public void startServer(int port) {

        executor.execute(() -> {

            try {

                serverSocket = new ServerSocket(port);

                notifyStatus("Waiting for WebRTC peer...");

                Socket client = serverSocket.accept();

                if (!running.get()) {
                    client.close();
                    return;
                }

                attachSocket(client);

            } catch (IOException e) {

                notifyError(e.getMessage());
            }
        });
    }

    public void connect(String host, int port) {

        executor.execute(() -> {

            try {

                Socket client = new Socket();

                client.connect(
                        new InetSocketAddress(host, port),
                        15000
                );

                if (!running.get()) {
                    client.close();
                    return;
                }

                attachSocket(client);

            } catch (IOException e) {

                notifyError(e.getMessage());
            }
        });
    }

    private void attachSocket(Socket connectedSocket)
            throws IOException {

        socket = connectedSocket;

        reader = new BufferedReader(
                new InputStreamReader(
                        socket.getInputStream(),
                        StandardCharsets.UTF_8
                )
        );

        writer = new BufferedWriter(
                new OutputStreamWriter(
                        socket.getOutputStream(),
                        StandardCharsets.UTF_8
                )
        );

        notifyConnected();

        executor.execute(this::readLoop);
    }

    public void send(JSONObject signal) {

        if (signal == null || !running.get()) {
            return;
        }

        executor.execute(() -> {

            synchronized (sendLock) {

                try {

                    if (writer == null ||
                            socket == null ||
                            socket.isClosed()) {

                        notifyError("Socket is not connected");
                        return;
                    }

                    writer.write(signal.toString());
                    writer.newLine();
                    writer.flush();

                } catch (IOException e) {

                    notifyError(e.getMessage());
                    closeSocket();
                }
            }
        });
    }

    private void readLoop() {

        try {

            String line;

            while (running.get() &&
                    socket != null &&
                    !socket.isClosed() &&
                    (line = reader.readLine()) != null) {

                try {

                    JSONObject signal = new JSONObject(line);

                    if (listener != null) {
                        listener.onSignal(signal);
                    }

                } catch (Exception e) {

                    notifyError("Invalid signaling message");
                }
            }

        } catch (IOException e) {

            if (running.get()) {
                notifyError(e.getMessage());
            }

        } finally {

            closeSocket();

            if (listener != null && running.get()) {
                listener.onDisconnected();
            }
        }
    }

    private void notifyConnected() {

        if (listener != null) {
            listener.onConnected();
        }
    }

    private void notifyStatus(String text) {

        if (listener != null) {
            listener.onError(text);
        }
    }

    private void notifyError(String error) {

        if (listener != null) {
            listener.onError(
                    error == null ? "Unknown socket error" : error
            );
        }
    }

    private void closeSocket() {

        Socket current = socket;
        socket = null;

        if (current != null) {
            try {
                current.close();
            } catch (IOException ignored) {
            }
        }

        reader = null;
        writer = null;
    }

    public void close() {

        if (!running.compareAndSet(true, false)) {
            return;
        }

        closeSocket();

        if (serverSocket != null) {

            try {
                serverSocket.close();
            } catch (IOException ignored) {
            }
        }

        executor.shutdownNow();
    }
}
