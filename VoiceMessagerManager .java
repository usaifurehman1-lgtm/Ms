package com.offlinemesh.chat;

import android.content.Context;
import android.media.MediaRecorder;
import android.media.MediaPlayer;
import android.os.Build;

import java.io.File;
import java.io.IOException;

public class VoiceMessageManager {

    private final Context context;

    private MediaRecorder recorder;
    private MediaPlayer player;

    private File currentRecording;

    public interface Callback {
        void onStarted();
        void onCompleted(File file);
        void onError(String error);
    }

    public VoiceMessageManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public synchronized void startRecording(Callback callback) {

        try {
            if (recorder != null) {
                stopAndReleaseRecorder();
            }

            File directory = new File(
                    context.getFilesDir(),
                    "voice_messages"
            );

            if (!directory.exists() && !directory.mkdirs()) {
                throw new IOException("Cannot create voice directory");
            }

            currentRecording = new File(
                    directory,
                    "voice_" + System.currentTimeMillis() + ".m4a"
            );

            if (Build.VERSION.SDK_INT >= 31) {
                recorder = new MediaRecorder(context);
            } else {
                recorder = new MediaRecorder();
            }

            recorder.setAudioSource(
                    MediaRecorder.AudioSource.MIC
            );

            recorder.setOutputFormat(
                    MediaRecorder.OutputFormat.MPEG_4
            );

            recorder.setAudioEncoder(
                    MediaRecorder.AudioEncoder.AAC
            );

            recorder.setAudioEncodingBitRate(96000);
            recorder.setAudioSamplingRate(44100);

            recorder.setOutputFile(
                    currentRecording.getAbsolutePath()
            );

            recorder.prepare();
            recorder.start();

            if (callback != null) {
                callback.onStarted();
            }

        } catch (Exception e) {

            stopAndReleaseRecorder();

            if (callback != null) {
                callback.onError(e.getMessage());
            }
        }
    }

    public synchronized void stopRecording(Callback callback) {

        if (recorder == null) {
            if (callback != null) {
                callback.onError("Recording is not active");
            }
            return;
        }

        File result = currentRecording;

        try {

            recorder.stop();

            if (result == null ||
                    !result.exists() ||
                    result.length() == 0) {

                throw new IOException("Recording file is empty");
            }

            stopAndReleaseRecorder();

            if (callback != null) {
                callback.onCompleted(result);
            }

        } catch (Exception e) {

            stopAndReleaseRecorder();

            if (result != null) {
                result.delete();
            }

            if (callback != null) {
                callback.onError("Recording failed: " + e.getMessage());
            }
        }
    }

    public synchronized void playRecording(
            File file,
            Callback callback
    ) {

        stopPlayback();

        if (file == null || !file.isFile()) {
            if (callback != null) {
                callback.onError("Audio file not found");
            }
            return;
        }

        try {

            player = new MediaPlayer();

            player.setDataSource(file.getAbsolutePath());

            player.setOnCompletionListener(mp -> stopPlayback());

            player.setOnErrorListener((mp, what, extra) -> {

                stopPlayback();

                if (callback != null) {
                    callback.onError("Audio playback failed");
                }

                return true;
            });

            player.prepare();
            player.start();

        } catch (Exception e) {

            stopPlayback();

            if (callback != null) {
                callback.onError(e.getMessage());
            }
        }
    }

    public synchronized void stopPlayback() {

        if (player != null) {

            try {
                if (player.isPlaying()) {
                    player.stop();
                }
            } catch (Exception ignored) {
            }

            player.release();
            player = null;
        }
    }

    private synchronized void stopAndReleaseRecorder() {

        if (recorder != null) {

            try {
                recorder.reset();
            } catch (Exception ignored) {
            }

            try {
                recorder.release();
            } catch (Exception ignored) {
            }

            recorder = null;
        }
    }

    public synchronized void release() {

        stopAndReleaseRecorder();
        stopPlayback();
    }
}
