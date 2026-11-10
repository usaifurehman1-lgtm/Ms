package com.offlinemesh.chat;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MeshCallController
        implements LocalSignalManager.Listener {

    public interface CallListener {
        void onOffer(SessionDescription offer);
        void onAnswer(SessionDescription answer);
        void onIceCandidate(IceCandidate candidate);
        void onConnected();
        void onDisconnected();
        void onError(String message);
    }

    private final LocalSignalManager signalManager;
    private final CallListener callListener;
    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    private volatile boolean closed = false;

    public MeshCallController(CallListener listener) {
        this.callListener = listener;
        this.signalManager =
                new LocalSignalManager(this);
    }

    public void startWaiting(int port) {
        if (!closed) {
            signalManager.startServer(port);
        }
    }

    public void connectToPeer(String host, int port) {
        if (!closed) {
            signalManager.connect(host, port);
        }
    }

    public void sendOffer(SessionDescription offer) {
        sendDescription("offer", offer);
    }

    public void sendAnswer(SessionDescription answer) {
        sendDescription("answer", answer);
    }

    private void sendDescription(
            String type,
            SessionDescription description) {

        if (closed || description == null) return;

        try {
            JSONObject message = new JSONObject();
            message.put("type", type);
            message.put("sdp", description.description);
            signalManager.send(message);

        } catch (JSONException e) {
            notifyError(e.getMessage());
        }
    }

    public void sendIceCandidate(IceCandidate candidate) {
        if (closed || candidate == null) return;

        try {
            JSONObject message = new JSONObject();

            message.put("type", "ice");
            message.put("sdpMid", candidate.sdpMid);
            message.put("sdpMLineIndex", candidate.sdpMLineIndex);
            message.put("candidate", candidate.sdp);

            signalManager.send(message);

        } catch (JSONException e) {
            notifyError(e.getMessage());
        }
    }

    @Override
    public void onSignal(JSONObject signal) {
        if (closed || signal == null) return;

        executor.execute(() -> {
            try {
                String type = signal.optString("type");

                switch (type) {

                    case "offer": {
                        String sdp = signal.getString("sdp");

                        SessionDescription offer =
                                new SessionDescription(
                                        SessionDescription.Type.OFFER,
                                        sdp
                                );

                        if (callListener != null) {
                            callListener.onOffer(offer);
                        }
                        break;
                    }

                    case "answer": {
                        String sdp = signal.getString("sdp");

                        SessionDescription answer =
                                new SessionDescription(
                                        SessionDescription.Type.ANSWER,
                                        sdp
                                );

                        if (callListener != null) {
                            callListener.onAnswer(answer);
                        }
                        break;
                    }

                    case "ice": {
                        String mid = signal.getString("sdpMid");
                        int index = signal.getInt("sdpMLineIndex");
                        String candidateText =
                                signal.getString("candidate");

                        IceCandidate candidate =
                                new IceCandidate(
                                        mid,
                                        index,
                                        candidateText
                                );

                        if (callListener != null) {
                            callListener.onIceCandidate(candidate);
                        }
                        break;
                    }

                    default:
                        notifyError("Unknown signal type: " + type);
                }

            } catch (Exception e) {
                notifyError("Signal processing failed: " +
                        e.getMessage());
            }
        });
    }

    @Override
    public void onConnected() {
        if (!closed && callListener != null) {
            callListener.onConnected();
        }
    }

    @Override
    public void onDisconnected() {
        if (!closed && callListener != null) {
            callListener.onDisconnected();
        }
    }

    @Override
    public void onError(String error) {
        notifyError(error);
    }

    private void notifyError(String message) {
        if (!closed && callListener != null) {
            callListener.onError(message);
        }
    }

    public void close() {
        if (closed) return;

        closed = true;
        signalManager.close();
        executor.shutdownNow();
    }
}
