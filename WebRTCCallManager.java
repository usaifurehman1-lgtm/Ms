package com.offlinemesh.chat;

import android.content.Context;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.DataChannel;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.RtpTransceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;
import org.webrtc.VideoCapturer;
import org.webrtc.CameraVideoCapturer;

import java.util.ArrayList;
import java.util.List;

public class WebRTCCallManager {

    public interface CallListener {
        void onLocalDescription(SessionDescription description);
        void onIceCandidate(IceCandidate candidate);
        void onRemoteVideo(VideoTrack track);
        void onCallState(String state);
        void onError(String error);
    }

    private final Context context;
    private final CallListener listener;

    private PeerConnectionFactory factory;
    private PeerConnection peerConnection;

    private EglBase eglBase;
    private SurfaceTextureHelper textureHelper;

    private VideoCapturer videoCapturer;
    private VideoSource videoSource;
    private AudioSource audioSource;

    private VideoTrack localVideoTrack;
    private AudioTrack localAudioTrack;

    private SurfaceViewRenderer localRenderer;
    private SurfaceViewRenderer remoteRenderer;

    private boolean initialized = false;

    public WebRTCCallManager(
            Context context,
            CallListener listener
    ) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    public void initialize(
            SurfaceViewRenderer local,
            SurfaceViewRenderer remote
    ) {

        try {

            localRenderer = local;
            remoteRenderer = remote;

            eglBase = EglBase.create();

            localRenderer.init(
                    eglBase.getEglBaseContext(),
                    null
            );

            remoteRenderer.init(
                    eglBase.getEglBaseContext(),
                    null
            );

            localRenderer.setMirror(true);
            remoteRenderer.setMirror(false);

            PeerConnectionFactory.initialize(
                    PeerConnectionFactory.InitializationOptions
                            .builder(context)
                            .createInitializationOptions()
            );

            PeerConnectionFactory.Options options =
                    new PeerConnectionFactory.Options();

            factory = PeerConnectionFactory.builder()
                    .setOptions(options)
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

            initialized = true;

        } catch (Exception e) {
            error(e.getMessage());
        }
    }

    public void createMedia() {

        if (!initialized) {
            error("Initialize WebRTC first");
            return;
        }

        try {

            textureHelper = SurfaceTextureHelper.create(
                    "CameraThread",
                    eglBase.getEglBaseContext()
            );

            videoCapturer = createCameraCapturer();

            if (videoCapturer == null) {
                throw new IllegalStateException(
                        "No camera available"
                );
            }

            videoSource = factory.createVideoSource(false);

            videoCapturer.initialize(
                    textureHelper,
                    context,
                    videoSource.getCapturerObserver()
            );

            videoCapturer.startCapture(1280, 720, 30);

            localVideoTrack = factory.createVideoTrack(
                    "video0",
                    videoSource
            );

            localVideoTrack.addSink(localRenderer);

            audioSource = factory.createAudioSource(
                    new MediaConstraints()
            );

            localAudioTrack = factory.createAudioTrack(
                    "audio0",
                    audioSource
            );

            if (listener != null) {
                listener.onCallState("Local media ready");
            }

        } catch (Exception e) {
            error(e.getMessage());
        }
    }

    private VideoCapturer createCameraCapturer() {

        Camera2Enumerator enumerator =
                new Camera2Enumerator(context);

        for (String name : enumerator.getDeviceNames()) {

            if (enumerator.isFrontFacing(name)) {

                VideoCapturer capturer =
                        enumerator.createCapturer(name, null);

                if (capturer != null) {
                    return capturer;
                }
            }
        }

        for (String name : enumerator.getDeviceNames()) {

            if (!enumerator.isFrontFacing(name)) {

                VideoCapturer capturer =
                        enumerator.createCapturer(name, null);

                if (capturer != null) {
                    return capturer;
                }
            }
        }

        return null;
    }

    public void createPeer(boolean audioOnly) {

        if (!initialized) {
            error("WebRTC not initialized");
            return;
        }

        List<PeerConnection.IceServer> iceServers =
                new ArrayList<>();

        PeerConnection.RTCConfiguration configuration =
                new PeerConnection.RTCConfiguration(iceServers);

        configuration.sdpSemantics =
                PeerConnection.SdpSemantics.UNIFIED_PLAN;

        peerConnection = factory.createPeerConnection(
                configuration,
                new PeerConnection.Observer() {

                    @Override
                    public void onSignalingChange(
                            PeerConnection.SignalingState state
                    ) {
                    }

                    @Override
                    public void onIceConnectionChange(
                            PeerConnection.IceConnectionState state
                    ) {
                        if (listener != null) {
                            listener.onCallState(
                                    "ICE: " + state
                            );
                        }
                    }

                    @Override
                    public void onIceConnectionReceivingChange(
                            boolean receiving
                    ) {
                    }

                    @Override
                    public void onIceGatheringChange(
                            PeerConnection.IceGatheringState state
                    ) {
                    }

                    @Override
                    public void onIceCandidate(
                            IceCandidate candidate
                    ) {
                        if (listener != null) {
                            listener.onIceCandidate(candidate);
                        }
                    }

                    @Override
                    public void onIceCandidatesRemoved(
                            IceCandidate[] candidates
                    ) {
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
                            MediaStream[] streams
                    ) {
                        if (receiver.track() instanceof VideoTrack) {
                            VideoTrack track =
                                    (VideoTrack) receiver.track();

                            if (remoteRenderer != null) {
                                track.addSink(remoteRenderer);
                            }

                            if (listener != null) {
                                listener.onRemoteVideo(track);
                            }
                        }
                    }
                }
        );

        if (peerConnection == null) {
            error("Peer connection creation failed");
            return;
        }

        if (localAudioTrack != null) {
            peerConnection.addTrack(localAudioTrack);
        }

        if (!audioOnly && localVideoTrack != null) {
            peerConnection.addTrack(localVideoTrack);
        }
    }

    public void createOffer() {

        if (peerConnection == null) {
            error("Create peer first");
            return;
        }

        peerConnection.createOffer(
                new SimpleSdpObserver() {

                    @Override
                    public void onCreateSuccess(
                            SessionDescription description
                    ) {

                        peerConnection.setLocalDescription(
                                new SimpleSdpObserver(),
                                description
                        );

                        if (listener != null) {
                            listener.onLocalDescription(description);
                        }
                    }

                    @Override
                    public void onCreateFailure(String message) {
                        error(message);
                    }
                },
                new MediaConstraints()
        );
    }

    public void createAnswer() {

        if (peerConnection == null) {
            error("Create peer first");
            return;
        }

        peerConnection.createAnswer(
                new SimpleSdpObserver() {

                    @Override
                    public void onCreateSuccess(
                            SessionDescription description
                    ) {

                        peerConnection.setLocalDescription(
                                new SimpleSdpObserver(),
                                description
                        );

                        if (listener != null) {
                            listener.onLocalDescription(description);
                        }
                    }

                    @Override
                    public void onCreateFailure(String message) {
                        error(message);
                    }
                },
                new MediaConstraints()
        );
    }

    public void setRemoteDescription(
            SessionDescription description
    ) {

        if (peerConnection == null) {
            error("Create peer first");
            return;
        }

        peerConnection.setRemoteDescription(
                new SimpleSdpObserver(),
                description
        );
    }

    public void addIceCandidate(IceCandidate candidate) {

        if (peerConnection != null) {
            peerConnection.addIceCandidate(candidate);
        }
    }

    public void setMicrophoneEnabled(boolean enabled) {

        if (localAudioTrack != null) {
            localAudioTrack.setEnabled(enabled);
        }
    }

    public void setCameraEnabled(boolean enabled) {

        if (localVideoTrack != null) {
            localVideoTrack.setEnabled(enabled);
        }
    }

    public void switchCamera() {

        if (videoCapturer instanceof CameraVideoCapturer) {

            ((CameraVideoCapturer) videoCapturer)
                    .switchCamera(null);
        }
    }

    public void closeCall() {

        try {

            if (peerConnection != null) {
                peerConnection.close();
                peerConnection.dispose();
                peerConnection = null;
            }

            if (videoCapturer != null) {

                try {
                    videoCapturer.stopCapture();
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }

                videoCapturer.dispose();
                videoCapturer = null;
            }

            if (localVideoTrack != null) {
                localVideoTrack.dispose();
                localVideoTrack = null;
            }

            if (localAudioTrack != null) {
                localAudioTrack.dispose();
                localAudioTrack = null;
            }

            if (videoSource != null) {
                videoSource.dispose();
                videoSource = null;
            }

            if (audioSource != null) {
                audioSource.dispose();
                audioSource = null;
            }

            if (textureHelper != null) {
                textureHelper.dispose();
                textureHelper = null;
            }

            if (localRenderer != null) {
                localRenderer.release();
            }

            if (remoteRenderer != null) {
                remoteRenderer.release();
            }

            if (listener != null) {
                listener.onCallState("Call ended");
            }

        } catch (Exception e) {
            error(e.getMessage());
        }
    }

    private void error(String message) {

        if (listener != null) {
            listener.onError(
                    message == null ? "Unknown WebRTC error" : message
            );
        }
    }

    public interface SdpCallback {
        void onSuccess(SessionDescription description);
        void onError(String error);
    }

    private static class SimpleSdpObserver
            implements SdpObserver {

        @Override
        public void onCreateSuccess(SessionDescription description) {
        }

        @Override
        public void onSetSuccess() {
        }

        @Override
        public void onCreateFailure(String error) {
        }

        @Override
        public void onSetFailure(String error) {
        }
    }
}
