package com.offlinemesh.chat;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ChatManager {

    private static final String PREFS = "mesh_chat";
    private static final String HISTORY = "messages";

    private final SharedPreferences preferences;
    private final P2PMeshNode meshNode;

    private final List<ChatMessage> messages =
            new ArrayList<>();

    public interface Listener {
        void onMessageAdded(ChatMessage message);
        void onMessageError(String error);
    }

    private Listener listener;

    public static class ChatMessage {

        public String id;
        public String sender;
        public String text;
        public long timestamp;
        public boolean outgoing;

        public ChatMessage(
                String id,
                String sender,
                String text,
                long timestamp,
                boolean outgoing
        ) {
            this.id = id;
            this.sender = sender;
            this.text = text;
            this.timestamp = timestamp;
            this.outgoing = outgoing;
        }
    }

    public ChatManager(Context context, P2PMeshNode node) {

        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);

        meshNode = node;

        loadHistory();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public synchronized boolean sendMessage(String text) {

        if (text == null || text.trim().isEmpty()) {
            notifyError("Message cannot be empty");
            return false;
        }

        String messageId = UUID.randomUUID().toString();

        boolean sent = meshNode.sendText(text);

        if (!sent) {
            notifyError("No connected peer");
            return false;
        }

        ChatMessage message = new ChatMessage(
                messageId,
                "Me",
                text,
                System.currentTimeMillis(),
                true
        );

        addMessage(message);

        return true;
    }

    public synchronized void receiveMessage(
            String sender,
            String text
    ) {

        ChatMessage message = new ChatMessage(
                UUID.randomUUID().toString(),
                sender,
                text,
                System.currentTimeMillis(),
                false
        );

        addMessage(message);
    }

    private synchronized void addMessage(ChatMessage message) {

        messages.add(message);

        saveHistory();

        if (listener != null) {
            listener.onMessageAdded(message);
        }
    }

    public synchronized List<ChatMessage> getMessages() {
        return new ArrayList<>(messages);
    }

    private synchronized void saveHistory() {

        try {

            JSONArray array = new JSONArray();

            for (ChatMessage message : messages) {

                JSONObject object = new JSONObject();

                object.put("id", message.id);
                object.put("sender", message.sender);
                object.put("text", message.text);
                object.put("timestamp", message.timestamp);
                object.put("outgoing", message.outgoing);

                array.put(object);
            }

            preferences.edit()
                    .putString(HISTORY, array.toString())
                    .apply();

        } catch (Exception e) {
            notifyError("Could not save chat history");
        }
    }

    private synchronized void loadHistory() {

        String saved = preferences.getString(HISTORY, "[]");

        try {

            JSONArray array = new JSONArray(saved);

            for (int i = 0; i < array.length(); i++) {

                JSONObject object = array.getJSONObject(i);

                messages.add(new ChatMessage(
                        object.getString("id"),
                        object.getString("sender"),
                        object.getString("text"),
                        object.getLong("timestamp"),
                        object.getBoolean("outgoing")
                ));
            }

        } catch (Exception e) {

            messages.clear();
        }
    }

    public synchronized void clearHistory() {

        messages.clear();

        preferences.edit()
                .remove(HISTORY)
                .apply();
    }

    private void notifyError(String error) {

        if (listener != null) {
            listener.onMessageError(error);
        }
    }
}
