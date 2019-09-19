package com.lvpengwei.androidvideoplayer.decoder;

import android.content.res.AssetFileDescriptor;
import android.util.Log;

import com.lvpengwei.androidvideoplayer.opengl.media.AudioFrame;

/**
 * Author: pengweilv
 * Date: 2019-09-18
 * Description:
 */
public class AudioReader {
    private static String TAG = "AudioReader ";
    private float seek_seconds;
    private boolean seek_req;
    private boolean seek_resp;
    private boolean isAudioOutputEOF;
    private MediaCodecAudioDecoder audioDecoder;
    private float position;

    public boolean startReading(AssetFileDescriptor afd) {
        position = 0.0f;
        seek_req = false;
        seek_resp = false;
        audioDecoder = new MediaCodecAudioDecoder();
        isAudioOutputEOF = false;
        return audioDecoder.OpenFile(afd);
    }

    public void stopReading() {
        audioDecoder.CloseFile();
    }

    public AudioFrame copyNexSample() {
        int ret = audioDecoder.GetNextVideoFrameForPlayback();
        if (ret == MediaCodecVideoDecoder.ERROR_EOF) {
            isAudioOutputEOF = true;
        } else if (ret == MediaCodecAudioDecoder.ERROR_FAIL) {
            Log.e(TAG, "decode audio error");
        }
        AudioFrame frame = new AudioFrame();
        frame.setBuffer(audioDecoder.getAudioBytes());
        frame.position = (float) (audioDecoder.GetTimestampOfCurrentTextureFrame() * 0.001 * 0.001);
        return frame;
    }

    public void closeFile() {
        audioDecoder.CloseFile();
    }

    public void setPosition(float seconds) {
        this.seek_seconds = seconds;
        this.seek_req = true;
        this.seek_resp = false;

        isAudioOutputEOF = false;
    }

    public void seek_frame() {
        long seek_target = (long) (seek_seconds * 1000000);
        audioDecoder.beforeSeek();
        audioDecoder.SeekVideoFrame(seek_target, 0);

//        if (mUploaderCallback != null) {
//            mUploaderCallback.onSeekCallback(seek_seconds);
//        } else {
//            Log.e(TAG, "VideoDecoder::mUploaderCallback is NULL");
//        }

        seek_resp = true;
    }

    public boolean isAudioEOF() {
        return isAudioOutputEOF;
    }

    public boolean hasSeekReq() {
        return seek_req;
    }

    public boolean hasSeekResp() {
        return seek_resp;
    }

    public int getAudioChannels() {
        return audioDecoder.getAudioChannels();
    }

    public int getAudioSampleRate() {
        return audioDecoder.getAudioSampleRate();
    }
}
