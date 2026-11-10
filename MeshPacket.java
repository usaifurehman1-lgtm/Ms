package com.offlinemesh.chat;

import org.json.JSONObject;

import java.util.UUID;

public class MeshPacket {

    public static final String TYPE_TEXT = "TEXT";
    public static final String TYPE_FILE = "FILE";
    public static final String TYPE_VOICE = "VOICE";
    public static final String TYPE_CALL = "CALL";

    public String packetId;
    public String senderId;
    public String destinationId;
    public String type;
    public String payload;
    public int ttl;
    public long timestamp;

    public MeshPacket(
            String senderId,
            String destinationId,
            String type,
            String payload,
            int ttl
    ) {
        this.packetId = UUID.randomUUID().toString();
        this.senderId = senderId;
        this.destinationId = destinationId;
        this.type = type;
        this.payload = payload;
        this.ttl = ttl;
        this.timestamp = System.currentTimeMillis();
    }

    public JSONObject toJson() throws Exception {

        JSONObject json = new JSONObject();

        json.put("packetId", packetId);
        json.put("senderId", senderId);
        json.put("destinationId", destinationId);
        json.put("type", type);
        json.put("payload", payload);
        json.put("ttl", ttl);
        json.put("timestamp", timestamp);

        return json;
    }

    public static MeshPacket fromJson(JSONObject json)
            throws Exception {

        MeshPacket packet = new MeshPacket(
                json.getString("senderId"),
                json.getString("destinationId"),
                json.getString("type"),
                json.getString("payload"),
                json.getInt("ttl")
        );

        packet.packetId = json.getString("packetId");
        packet.timestamp = json.getLong("timestamp");

        return packet;
    }

    public MeshPacket forward() {

        if (ttl <= 1) {
            return null;
        }

        MeshPacket forwarded = new MeshPacket(
                senderId,
                destinationId,
                type,
                payload,
                ttl - 1
        );

        forwarded.packetId = packetId;
        forwarded.timestamp = timestamp;

        return forwarded;
    }
}
