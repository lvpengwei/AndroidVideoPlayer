package com.lvpengwei.androidvideoplayer.player;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;

public class AudioOutput {
    private static String TAG = "AudioOutput";

    private static final int WRITE_DATA = 1;

    private Handler handler;

    public interface AudioOutputCallback {
        byte[] produceData(int bufferSize);
    }

    enum PlayingState {
        init,
        stopped,
        playing
    }

    public PlayingState playingState = PlayingState.init;

    private int accompanySampleRate;
    private int channels;
    private AudioOutputCallback produceDataCallback;
    private AudioTrack mAudioTrack;

    private int bufferSize;
    private long delayTime;
    private HandlerThread thread;

    public void init(final int channels, final int accompanySampleRate, AudioOutputCallback callback) {
        this.produceDataCallback = callback;
        this.accompanySampleRate = accompanySampleRate;
        this.channels = channels;
        bufferSize = AudioTrack.getMinBufferSize(accompanySampleRate, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT);
        delayTime = (long) (bufferSize * 1000.0 / (accompanySampleRate * 2 * channels));
        mAudioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, accompanySampleRate, channels, AudioFormat.ENCODING_PCM_16BIT, bufferSize, AudioTrack.MODE_STREAM);
        setupHandler();
    }

    private void producePacket() {
        if (playingState != PlayingState.playing) return;
        byte[] buffer = produceDataCallback.produceData(bufferSize);
        if (buffer == null || buffer.length <= 0) {
            sendMessage(WRITE_DATA, delayTime);
            return;
        }
        mAudioTrack.write(buffer, 0, buffer.length);
        sendMessage(WRITE_DATA, delayTime);
    }

    private void sendMessage(int msgId, long delayMillis) {
        if (handler == null) return;
        Message message = handler.obtainMessage(msgId);
        handler.sendMessageDelayed(message, delayMillis);
    }

    public void start() {
        if (mAudioTrack == null) return;
        playingState = PlayingState.playing;
        mAudioTrack.play();
        sendMessage(WRITE_DATA, 0);
    }

    public void play() {
        if (mAudioTrack == null) return;
        mAudioTrack.play();
        playingState = PlayingState.playing;
        sendMessage(WRITE_DATA, 0);
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

    private void setupHandler() {
        thread = new HandlerThread("AudioOutput");
        thread.start();
        handler = new Handler(thread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case WRITE_DATA:
                        producePacket();
                        break;
                    default:
                        break;
                }
            }
        };
    }
}
