package com.offlinemesh.chat;

import android.app.Activity;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

public final class ChatBridge {

    private final Activity activity;
    private final WebView webView;
    private final ChatManager chatManager;

    private final String chatId;
    private final String senderId;
    private final String receiverId;

    public ChatBridge(
            Activity activity,
            WebView webView,
            ChatManager chatManager,
            String chatId,
            String senderId,
            String receiverId
    ) {
        if (activity == null || webView == null ||
                chatManager == null || chatId == null ||
                senderId == null || receiverId == null) {
            throw new IllegalArgumentException(
                    "ChatBridge parameters cannot be null"
            );
        }

        this.activity = activity;
        this.webView = webView;
        this.chatManager = chatManager;
        this.chatId = chatId;
        this.senderId = senderId;
        this.receiverId = receiverId;
    }

    @JavascriptInterface
    public void sendText(String text) {
        if (text == null || text.trim().isEmpty()) {
            sendError("Message cannot be empty.");
            return;
        }

        chatManager.sendText(
                chatId,
                senderId,
                receiverId,
                text
        );
    }

    @JavascriptInterface
    public void loadMessages() {
        chatManager.getMessages(
                chatId,
                new ChatManager.MessagesCallback() {
                    @Override
                    public void onLoaded(
                            List<ChatManager.Message> messages
                    ) {
                        try {
                            JSONArray array = new JSONArray();

                            for (ChatManager.Message message : messages) {
                                JSONObject item = new JSONObject();

                                item.put("id", message.id);
                                item.put("chatId", message.chatId);
                                item.put("senderId", message.senderId);
                                item.put("receiverId", message.receiverId);
                                item.put("text", message.text);
                                item.put("type", message.type);
                                item.put("status", message.status);
                                item.put("timestamp", message.timestamp);

                                array.put(item);
                            }

                            callJavaScript(
                                    "window.onChatMessages",
                                    array.toString()
                            );

                        } catch (Exception e) {
                            sendError(e.getMessage());
                        }
                    }

                    @Override
                    public void onError(String error) {
                        sendError(error);
                    }
                }
        );
    }

    @JavascriptInterface
    public void markAsRead(String messageId) {
        if (messageId != null && !messageId.trim().isEmpty()) {
            chatManager.markAsRead(messageId);
        }
    }

    @JavascriptInterface
    public void deleteMessage(String messageId) {
        if (messageId != null && !messageId.trim().isEmpty()) {
            chatManager.deleteMessage(messageId);
        }
    }

    @JavascriptInterface
    public void deleteConversation() {
        chatManager.deleteConversation(chatId);
    }

    @JavascriptInterface
    public String getBridgeStatus() {
        return "CHAT_BRIDGE_READY";
    }

    private void sendError(String error) {
        try {
            JSONObject object = new JSONObject();
            object.put(
                    "error",
                    error == null ? "Unknown error" : error
            );

            callJavaScript(
                    "window.onChatError",
                    object.toString()
            );

        } catch (Exception ignored) {
            // Ignore JSON construction errors.
        }
    }

    private void callJavaScript(
            String function,
            String json
    ) {
        activity.runOnUiThread(() -> {
            if (activity.isFinishing() || activity.isDestroyed()) {
                return;
            }

            webView.evaluateJavascript(
                    function + " && " + function + "(" + json + ")",
                    null
            );
        });
    }
}
