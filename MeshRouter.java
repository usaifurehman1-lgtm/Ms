package com.offlinemesh.chat;

import java.util.UUID;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class MeshRouter {

    private static final int DEFAULT_TTL = 16;

    private final P2PMeshNode node;

    private final Set<String> processed =
            ConcurrentHashMap.newKeySet();

    public interface RouteListener {
        void onDelivered(String messageId);
        void onReceived(String sender, String message);
    }

    private RouteListener listener;

    public MeshRouter(P2PMeshNode node) {
        this.node = node;
    }

    public void setListener(RouteListener listener) {
        this.listener = listener;
    }

    public String sendMessage(String message) {

        if (message == null || message.trim().isEmpty()) {
            return null;
        }

        String messageId = UUID.randomUUID().toString();

        boolean sent = node.sendText(message);

        if (sent) {

            processed.add(messageId);

            if (listener != null) {
                listener.onDelivered(messageId);
            }

            return messageId;
        }

        return null;
    }

    public boolean hasProcessed(String messageId) {
        return processed.contains(messageId);
    }

    public void clearOldPackets() {
        processed.clear();
    }
}
