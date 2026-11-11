package com.haye.app;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;

import androidx.core.content.ContextCompat;

import org.json.JSONObject;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.DataChannel;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.JavaAudioDeviceModule;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HayeCallBridge {

    public interface Listener {
        void onStatus(String status);
        void onIncomingCall(boolean video);
        void onCallConnected();
        void onCallEnded();
        void onRemoteVideo(VideoTrack track);
        void onError(String error);
    }

    private static final int SIGNAL_PORT = 8990;

    private final Activity activity;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService executor =
            Executors.newCachedThreadPool();

    private final boolean groupOwner;
    private final String ownerAddress;

    private ServerSocket serverSocket;
    private Socket signalSocket;
    private BufferedWriter writer;

    private volatile boolean signalingReady = false;
    private volatile boolean running = true;

    private PeerConnectionFactory factory;
    private PeerConnection peerConnection;

    private AudioSource audioSource;
    private AudioTrack audioTrack;

    private VideoSource videoSource;
    private VideoTrack videoTrack;
    private VideoCapturer videoCapturer;
    private SurfaceTextureHelper textureHelper;

    private EglBase eglBase;
    private SurfaceViewRenderer localRenderer;
    private SurfaceViewRenderer remoteRenderer;

    private AudioManager audioManager;

    private boolean videoCall = false;
    private boolean muted = false;
    private boolean cameraEnabled = true;
    private boolean speakerEnabled = true;

    private final List<IceCandidate> pendingCandidates =
            new ArrayList<>();

    public HayeCallBridge(
            Activity activity,
            boolean groupOwner,
            String ownerAddress,
            Listener listener
    ) {
        this.activity = activity;
        this.groupOwner = groupOwner;
        this.ownerAddress = ownerAddress;
        this.listener = listener;

        audioManager = (AudioManager)
                activity.getSystemService(Context.AUDIO_SERVICE);

        initializeWebRTC();
        initializeSignaling();
    }

    private void status(String message) {
        main.post(() -> listener.onStatus(message));
    }

    private void error(String message) {
        main.post(() -> listener.onError(message));
    }

    public static String[] requiredPermissions() {
        return new String[]{
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.CAMERA
        };
    }

    public boolean hasPermissions(boolean video) {
        boolean audioGranted =
                ContextCompat.checkSelfPermission(
                        activity,
                        Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED;

        if (!audioGranted) return false;

        if (!video) return true;

        return ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED;
    }

    private void initializeWebRTC() {
        try {
            PeerConnectionFactory.initialize(
                    PeerConnectionFactory.InitializationOptions
                            .builder(activity)
                            .createInitializationOptions()
            );

            eglBase = EglBase.create();

            JavaAudioDeviceModule audioModule =
                    JavaAudioDeviceModule.builder(activity)
                            .createAudioDeviceModule();

            factory = PeerConnectionFactory.builder()
                    .setAudioDeviceModule(audioModule)
                    .setVideoEncoderFactory(
                            new DefaultVideoEncoderFactory(
                                    eglBase.getEglBaseContext(),
                                    true,
                                    true
                            )
                    )
                    .setVideoDecoderFactory(
                            new DefaultVideoDecoderFactory(
                                    eglBase.getEglBaseContext()
                            )
                    )
                    .createPeerConnectionFactory();

            audioModule.release();

        } catch (Exception e) {
            error("WebRTC initialization failed: " + e.getMessage());
        }
    }

    public void setRenderers(
            SurfaceViewRenderer local,
            SurfaceViewRenderer remote
    ) {
        localRenderer = local;
        remoteRenderer = remote;

        if (eglBase == null) return;

        if (localRenderer != null) {
            localRenderer.init(
                    eglBase.getEglBaseContext(),
                    null
            );
            localRenderer.setMirror(true);
        }

        if (remoteRenderer != null) {
            remoteRenderer.init(
                    eglBase.getEglBaseContext(),
                    null
            );
            remoteRenderer.setMirror(false);
        }
    }

    // Owner phone TCP signaling server.
    // Client phone TCP signaling connection.

    private void initializeSignaling() {
        executor.execute(() -> {
            try {
                if (groupOwner) {
                    serverSocket = new ServerSocket(SIGNAL_PORT);
                    status("Call signaling server ready");

                    signalSocket = serverSocket.accept();

                } else {
                    if (ownerAddress == null ||
                            ownerAddress.trim().isEmpty()) {
                        throw new Exception(
                                "Wi-Fi Direct group owner address missing"
                        );
                    }

                    signalSocket = new Socket(
                            ownerAddress,
                            SIGNAL_PORT
                    );
                }

                writer = new BufferedWriter(
                        new OutputStreamWriter(
                                signalSocket.getOutputStream()
                        )
                );

                signalingReady = true;
                status("Call signaling connected");

                readSignals();

            } catch (Exception e) {
                if (running) {
                    error("Call signaling failed: " + e.getMessage());
                }
            }
        });
    }

    private void readSignals() {
        try {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(
                            signalSocket.getInputStream()
                    )
            );

            String line;

            while (running &&
                    (line = reader.readLine()) != null) {

                JSONObject signal = new JSONObject(line);

                main.post(() -> handleSignal(signal));
            }

        } catch (Exception e) {
            if (running) {
                error("Signaling connection lost: " + e.getMessage());
            }
        }
    }

    private synchronized void sendSignal(JSONObject signal) {
        try {
            if (!signalingReady || writer == null) {
                throw new Exception(
                        "Call signaling is not connected"
                );
            }

            writer.write(signal.toString());
            writer.newLine();
            writer.flush();

        } catch (Exception e) {
            error("Signal sending failed: " + e.getMessage());
        }
    }

    private void initializePeerConnection() {
        if (peerConnection != null) return;

        try {
            // Offline Wi-Fi Direct call:
            // No STUN or TURN internet server is configured.
            PeerConnection.RTCConfiguration configuration =
                    new PeerConnection.RTCConfiguration(
                            Collections.emptyList()
                    );

            configuration.sdpSemantics =
                    PeerConnection.SdpSemantics.UNIFIED_PLAN;

            peerConnection = factory.createPeerConnection(
                    configuration,
                    new PeerConnection.Observer() {

                @Override
                public void onSignalingChange(
                        PeerConnection.SignalingState state) {
                }

                @Override
                public void onIceConnectionChange(
                        PeerConnection.IceConnectionState state) {

                    if (state ==
                            PeerConnection.IceConnectionState.CONNECTED) {
                        main.post(listener::onCallConnected);
                        status("Call connected");
                    }

                    if (state ==
                            PeerConnection.IceConnectionState.DISCONNECTED ||
                            state ==
                            PeerConnection.IceConnectionState.FAILED ||
                            state ==
                            PeerConnection.IceConnectionState.CLOSED) {

                        status("Call connection interrupted");
                    }
                }

                @Override
                public void onIceConnectionReceivingChange(
                        boolean receiving) {
                }

                @Override
                public void onIceGatheringChange(
                        PeerConnection.IceGatheringState state) {
                }

                @Override
                public void onIceCandidate(IceCandidate candidate) {
                    try {
                        JSONObject json = new JSONObject();
                        json.put("type", "candidate");
                        json.put("sdp", candidate.sdp);
                        json.put("mid", candidate.sdpMid);
                        json.put("line", candidate.sdpMLineIndex);

                        sendSignal(json);

                    } catch (Exception e) {
                        error(e.getMessage());
                    }
                }

                @Override
                public void onIceCandidatesRemoved(
                        IceCandidate[] candidates) {
                }

                @Override
                public void onAddStream(MediaStream stream) {
                }

                @Override
                public void onRemoveStream(MediaStream stream) {
                }

                @Override
                public void onDataChannel(DataChannel channel) {
                }

                @Override
                public void onRenegotiationNeeded() {
                }

                @Override
                public void onAddTrack(
                        RtpReceiver receiver,
                        MediaStream[] streams) {

                    if (receiver.track() instanceof VideoTrack) {
                        VideoTrack remoteTrack =
                                (VideoTrack) receiver.track();

                        main.post(() -> {
                            if (remoteRenderer != null) {
                                remoteTrack.addSink(remoteRenderer);
                            }

                            listener.onRemoteVideo(remoteTrack);
                        });
                    }
                }
            });

            if (peerConnection == null) {
                throw new Exception(
                        "Could not create PeerConnection"
                );
            }

            addLocalMedia();

        } catch (Exception e) {
            error("PeerConnection error: " + e.getMessage());
        }
    }

    private void addLocalMedia() {
        if (peerConnection == null) return;

        try {
            MediaConstraints audioConstraints =
                    new MediaConstraints();

            audioSource = factory.createAudioSource(
                    audioConstraints
            );

            audioTrack = factory.createAudioTrack(
                    "HAYE_AUDIO",
                    audioSource
            );

            peerConnection.addTrack(
                    audioTrack,
                    Collections.singletonList("HAYE_STREAM")
            );

            if (videoCall) {
                createCameraTrack();
            }

        } catch (Exception e) {
            error("Local media error: " + e.getMessage());
        }
    }

    private void createCameraTrack() {
        try {
            CameraEnumerator enumerator =
                    new Camera2Enumerator(activity);

            String cameraName = null;

            for (String name : enumerator.getDeviceNames()) {
                if (enumerator.isFrontFacing(name)) {
                    cameraName = name;
                    break;
                }
            }

            if (cameraName == null) {
                for (String name : enumerator.getDeviceNames()) {
                    if (enumerator.isBackFacing(name)) {
                        cameraName = name;
                        break;
                    }
                }
            }

            if (cameraName == null) {
                throw new Exception("No camera available");
            }

            videoCapturer = enumerator.createCapturer(
                    cameraName,
                    null
            );

            if (videoCapturer == null) {
                throw new Exception("Camera initialization failed");
            }

            textureHelper = SurfaceTextureHelper.create(
                    "HAYE_CAMERA_THREAD",
                    eglBase.getEglBaseContext()
            );

            videoSource = factory.createVideoSource(
                    videoCapturer.isScreencast()
            );

            videoCapturer.initialize(
                    textureHelper,
                    activity,
                    videoSource.getCapturerObserver()
            );

            videoCapturer.startCapture(640, 480, 24);

            videoTrack = factory.createVideoTrack(
                    "HAYE_VIDEO",
                    videoSource
            );

            if (localRenderer != null) {
                videoTrack.addSink(localRenderer);
            }

            peerConnection.addTrack(
                    videoTrack,
                    Collections.singletonList("HAYE_STREAM")
            );

        } catch (Exception e) {
            error("Camera error: " + e.getMessage());
        }
    }

    // Start outgoing audio call.

    public void startAudioCall() {
        startOutgoingCall(false);
    }

    // Start outgoing video call.

    public void startVideoCall() {
        startOutgoingCall(true);
    }

    private void startOutgoingCall(boolean video) {
        if (!hasPermissions(video)) {
            error("Microphone/camera permission is missing");
            return;
        }

        if (!signalingReady) {
            error("Wait for call signaling connection");
            return;
        }

        if (peerConnection != null) {
            error("A call is already active");
            return;
        }

        videoCall = video;
        initializePeerConnection();

        if (peerConnection == null) return;

        MediaConstraints constraints = new MediaConstraints();

        peerConnection.createOffer(
                new SimpleSdpObserver() {
                    @Override
                    public void onCreateSuccess(
                            SessionDescription description) {

                        peerConnection.setLocalDescription(
                                new SimpleSdpObserver() {
                                    @Override
                                    public void onSetSuccess() {
                                        sendDescription(
                                                "offer",
                                                description
                                        );
                                    }

                                    @Override
                                    public void onSetFailure(String message) {
                                        error(message);
                                    }
                                },
                                description
                        );
                    }

                    @Override
                    public void onCreateFailure(String message) {
                        error("Offer failed: " + message);
                    }
                },
                constraints
        );

        status(video
                ? "Starting video call..."
                : "Starting audio call...");
    }

    // Incoming call acceptance.

    public void acceptCall() {
        if (peerConnection != null) {
            error("Call is already active");
            return;
        }

        if (!hasPermissions(videoCall)) {
            error("Required permissions are missing");
            return;
        }

        if (pendingOffer == null) {
            error("No incoming call found");
            return;
        }

        initializePeerConnection();

        if (peerConnection == null) return;

        SessionDescription offer = pendingOffer;
        pendingOffer = null;

        peerConnection.setRemoteDescription(
                new SimpleSdpObserver() {
                    @Override
                    public void onSetSuccess() {
                        applyPendingCandidates();
                        createAnswer();
                    }

                    @Override
                    public void onSetFailure(String message) {
                        error("Offer rejected: " + message);
                    }
                },
                offer
        );
    }

    private SessionDescription pendingOffer;

    private void createAnswer() {
        peerConnection.createAnswer(
                new SimpleSdpObserver() {
                    @Override
                    public void onCreateSuccess(
                            SessionDescription description) {

                        peerConnection.setLocalDescription(
                                new SimpleSdpObserver() {
                                    @Override
                                    public void onSetSuccess() {
                                        sendDescription(
                                                "answer",
                                                description
                                        );
                                    }

                                    @Override
                                    public void onSetFailure(String message) {
                                        error(message);
                                    }
                                },
                                description
                        );
                    }

                    @Override
                    public void onCreateFailure(String message) {
                        error("Answer failed: " + message);
                    }
                },
                new MediaConstraints()
        );
    }

    private void sendDescription(
            String type,
            SessionDescription description
    ) {
        try {
            JSONObject json = new JSONObject();
            json.put("type", type);
            json.put("sdp", description.description);

            sendSignal(json);

        } catch (Exception e) {
            error(e.getMessage());
        }
    }

    private void handleSignal(JSONObject json) {
        try {
            String type = json.getString("type");

            if ("offer".equals(type)) {
                if (peerConnection != null) {
                    sendEndSignal();
                    return;
                }

                String sdp = json.getString("sdp");

                videoCall = sdp.contains("m=video");

                pendingOffer = new SessionDescription(
                        SessionDescription.Type.OFFER,
                        sdp
                );

                listener.onIncomingCall(videoCall);

                status(videoCall
                        ? "Incoming video call"
                        : "Incoming audio call");
            }

            else if ("answer".equals(type)) {
                if (peerConnection == null) return;

                SessionDescription answer =
                        new SessionDescription(
                                SessionDescription.Type.ANSWER,
                                json.getString("sdp")
                        );

                peerConnection.setRemoteDescription(
                        new SimpleSdpObserver() {
                            @Override
                            public void onSetSuccess() {
                                applyPendingCandidates();
                            }

                            @Override
                            public void onSetFailure(String message) {
                                error("Answer error: " + message);
                            }
                        },
                        answer
                );
            }

            else if ("candidate".equals(type)) {
                IceCandidate candidate = new IceCandidate(
                        json.optString("mid"),
                        json.optInt("line"),
                        json.getString("sdp")
                );

                if (peerConnection == null ||
                        peerConnection.getRemoteDescription() == null) {
                    pendingCandidates.add(candidate);
                } else {
                    peerConnection.addIceCandidate(candidate);
                }
            }

            else if ("end".equals(type)) {
                closePeerConnection();
                main.post(listener::onCallEnded);
                status("Call ended by remote device");
            }

        } catch (Exception e) {
            error("Signal handling error: " + e.getMessage());
        }
    }

    private void applyPendingCandidates() {
        if (peerConnection == null) return;

        for (IceCandidate candidate :
                new ArrayList<>(pendingCandidates)) {

            peerConnection.addIceCandidate(candidate);
        }

        pendingCandidates.clear();
    }

    // Microphone controls.

    public void setMuted(boolean value) {
        muted = value;

        if (audioTrack != null) {
            audioTrack.setEnabled(!muted);
        }

        status(muted ? "Microphone muted" : "Microphone enabled");
    }

    public void toggleMute() {
        setMuted(!muted);
    }

    // Camera controls.

    public void setCameraEnabled(boolean enabled) {
        cameraEnabled = enabled;

        if (videoTrack != null) {
            videoTrack.setEnabled(cameraEnabled);
        }

        status(cameraEnabled
                ? "Camera enabled"
                : "Camera disabled");
    }

    public void toggleCamera() {
        setCameraEnabled(!cameraEnabled);
    }

    // Speaker controls.

    public void setSpeakerEnabled(boolean enabled) {
        speakerEnabled = enabled;

        audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
        audioManager.setSpeakerphoneOn(speakerEnabled);

        status(speakerEnabled
                ? "Speaker enabled"
                : "Speaker disabled");
    }

    public void toggleSpeaker() {
        setSpeakerEnabled(!speakerEnabled);
    }

    // Switch front/back camera.

    public void switchCamera() {
        if (videoCapturer instanceof CameraVideoCapturer) {
            ((CameraVideoCapturer) videoCapturer)
                    .switchCamera(null);
        }
    }

    private void sendEndSignal() {
        try {
            JSONObject json = new JSONObject();
            json.put("type", "end");
            sendSignal(json);
        } catch (Exception ignored) {
        }
    }

    public void endCall() {
        sendEndSignal();
        closePeerConnection();

        main.post(listener::onCallEnded);
        status("Call ended");
    }

    private void closePeerConnection() {
        if (peerConnection != null) {
            peerConnection.close();
            peerConnection.dispose();
            peerConnection = null;
        }

        if (videoCapturer != null) {
            try {
                videoCapturer.stopCapture();
            } catch (Exception ignored) {
            }

            videoCapturer.dispose();
            videoCapturer = null;
        }

        if (videoTrack != null) {
            videoTrack.dispose();
            videoTrack = null;
        }

        if (videoSource != null) {
            videoSource.dispose();
            videoSource = null;
        }

        if (textureHelper != null) {
            textureHelper.dispose();
            textureHelper = null;
        }

        if (audioTrack != null) {
            audioTrack.dispose();
            audioTrack = null;
        }

        if (audioSource != null) {
            audioSource.dispose();
            audioSource = null;
        }

        pendingCandidates.clear();

        if (audioManager != null) {
            audioManager.setSpeakerphoneOn(false);
            audioManager.setMode(AudioManager.MODE_NORMAL);
        }
    }

    public void close() {
        running = false;
        signalingReady = false;

        closePeerConnection();

        try {
            if (signalSocket != null) signalSocket.close();
        } catch (Exception ignored) {
        }

        try {
            if (serverSocket != null) serverSocket.close();
        } catch (Exception ignored) {
        }

        if (factory != null) {
            factory.dispose();
            factory = null;
        }

        if (localRenderer != null) {
            localRenderer.release();
        }

        if (remoteRenderer != null) {
            remoteRenderer.release();
        }

        if (eglBase != null) {
            eglBase.release();
            eglBase = null;
        }

        executor.shutdownNow();
    }

    private static class SimpleSdpObserver implements SdpObserver {

        @Override
        public void onCreateSuccess(SessionDescription description) {
        }

        @Override
        public void onSetSuccess() {
        }

        @Override
        public void onCreateFailure(String message) {
        }

        @Override
        public void onSetFailure(String message) {
        }
    }
}
