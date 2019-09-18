package com.lvpengwei.androidvideoplayer.player;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;

public class AudioOutput {
    private static String TAG = "AudioOutput";
    public static double DEFAULT_AUDIO_BUFFER_DURATION_IN_SECS = 0.03;

    public interface AudioOutputCallback {
        byte[] produceData(int bufferSize, Object ctx);
    }

    enum PlayingState {
        init,
        stopped,
        playing
    }

    public PlayingState playingState = PlayingState.init;

    private AudioOutputCallback produceDataCallback;
    private AudioTrack mAudioTrack;
    private Object ctx;

    private int bufferSize;

    public void init(int channels, int accompanySampleRate, AudioOutputCallback callback, Object ctx) {
        this.produceDataCallback = callback;
        this.ctx = ctx;
        this.bufferSize = (int) (channels * accompanySampleRate * 2 * DEFAULT_AUDIO_BUFFER_DURATION_IN_SECS);
        int outputBufferSize = AudioTrack.getMinBufferSize(accompanySampleRate, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT);
        mAudioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, accompanySampleRate, channels, AudioFormat.ENCODING_PCM_16BIT, outputBufferSize, AudioTrack.MODE_STREAM);
//        mAudioTrack.setPositionNotificationPeriod(bufferSize / 2);
        mAudioTrack.setPositionNotificationPeriod(200);
        mAudioTrack.setPlaybackPositionUpdateListener(new AudioTrack.OnPlaybackPositionUpdateListener() {
            @Override
            public void onMarkerReached(AudioTrack track) {
                Log.i(TAG, "");
            }

            @Override
            public void onPeriodicNotification(AudioTrack track) {
                producePacket();
            }
        });
    }

    private void producePacket() {
        if (playingState != PlayingState.playing) return;
        byte[] buffer = produceDataCallback.produceData(bufferSize, ctx);
        if (buffer == null || buffer.length <= 0) return;
        if (playingState != PlayingState.playing) return;
        mAudioTrack.write(buffer, 0, buffer.length);
    }

    public void start() {
        if (mAudioTrack == null) return;
        playingState = PlayingState.playing;
        producePacket();
        mAudioTrack.play();
    }

    public void play() {
        if (mAudioTrack == null) return;
        mAudioTrack.play();
        playingState = PlayingState.playing;
    }

    public void pause() {
        if (mAudioTrack == null) return;
        mAudioTrack.pause();
    }

    public void stop() {
        if (mAudioTrack == null) return;
        mAudioTrack.pause();
        destroy();
        playingState = PlayingState.stopped;
    }

    public boolean isPlaying() {
        if (mAudioTrack == null) return false;
        return mAudioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING;
    }

    public void destroy() {
        mAudioTrack.release();
    }

}
