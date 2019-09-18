package com.lvpengwei.androidvideoplayer.decoder;

import android.util.Log;

import com.lvpengwei.androidvideoplayer.opengl.media.texture.TextureFrame;
import com.lvpengwei.androidvideoplayer.opengl.media.texture.uploader.GPUTextureFrameUploader;
import com.lvpengwei.androidvideoplayer.opengl.media.texture.uploader.TextureFrameUploader;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class VideoDecoder {
    private static String TAG = "VideoDecoder";
    private TextureFrameUploader textureFrameUploader;
    private Lock mLock;
    private Condition mCondition;
    private TextureFrameUploader.UploaderCallback mUploaderCallback;
    private int width;
    private int height;
    private int degress;
    private float seek_seconds;
    private boolean seek_req;
    private boolean seek_resp;
    private boolean isVideoOutputEOF;
    private boolean isAudioOutputEOF;
    private MediaCodecVideoDecoder videoDecoder;
    private MediaCodecAudioDecoder audioDecoder;
    private float position;
    private DecoderRequestHeader requestHeader;
    private int decodeTexId;
    private boolean isMediaCodecInit;
    public VideoDecoder() {
    }

    public void openFile(DecoderRequestHeader requestHeader) {
        position = 0.0f;
        seek_req = false;
        seek_resp = false;
        mLock = new ReentrantLock();
        mCondition = mLock.newCondition();
        this.requestHeader = requestHeader;
        videoDecoder = new MediaCodecVideoDecoder();
        audioDecoder = new MediaCodecAudioDecoder();
        isVideoOutputEOF = false;
        isAudioOutputEOF = false;
//        audioDecoder.OpenFile(requestHeader.getUri());
    }

    public void decode() {
        if (!isMediaCodecInit) {
            initializeMediaCodec();
            isMediaCodecInit = true;
        }
        int ret = videoDecoder.GetNextVideoFrameForPlayback();
        if (ret == MediaCodecVideoDecoder.ERROR_OK) {
            uploadTexture();
        } else if (ret == MediaCodecVideoDecoder.ERROR_EOF) {
            isVideoOutputEOF = true;
        } else {
            Log.e(TAG, "decode video error");
        }
    }

    public byte[] decodeAudio() {
        int ret = audioDecoder.GetNextVideoFrameForPlayback();
        if (ret == MediaCodecVideoDecoder.ERROR_EOF) {
            isVideoOutputEOF = true;
        } else if (ret == MediaCodecAudioDecoder.ERROR_FAIL) {
            Log.e(TAG, "decode audio error");
        }
        return audioDecoder.getAudioBytes();
    }

    private void initializeMediaCodec() {
        decodeTexId = ((GPUTextureFrameUploader) textureFrameUploader).getDecodeTexId();
//        videoDecoder.OpenFile(requestHeader.getUri(), decodeTexId);
    }

    private TextureFrameUploader createTextureFrameUploader() {
        return new GPUTextureFrameUploader();
    }

    private float updateTexImage(TextureFrame textureFrame) {
        return videoDecoder.updateTexImage();
    }

    public void closeFile() {
        videoDecoder.CloseFile();
        audioDecoder.CloseFile();
    }

    public void startUploader(TextureFrameUploader.UploaderCallback uploaderCallback) {
        mUploaderCallback = uploaderCallback;
        textureFrameUploader = createTextureFrameUploader();
        textureFrameUploader.registerUpdateTexImageCallback(new TextureFrameUploader.UpdateTexImageCallback() {
            @Override
            public float call(TextureFrame textureFrame, Object context) {
                return updateTexImage(textureFrame);
            }
        }, new TextureFrameUploader.SignalDecodeThreadCallback() {
            @Override
            public void call(Object context) {
                signalDecodeThread();
            }
        }, this);
        textureFrameUploader.setUploaderCallback(uploaderCallback);
        textureFrameUploader.start(width, height, degress);
        mLock.lock();
        mCondition.awaitUninterruptibly();
        mLock.unlock();
    }

    public void uploadTexture() {
        mLock.lock();
        try {
            textureFrameUploader.signalFrameAvailable();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        mCondition.awaitUninterruptibly();
        mLock.unlock();
    }

    public void signalDecodeThread() {
        mLock.lock();
        mCondition.signal();
        mLock.unlock();
    }

    public void setPosition(float seconds) {
        this.seek_seconds = seconds;
        this.seek_req = true;
        this.seek_resp = false;

        isVideoOutputEOF = false;
        isAudioOutputEOF = false;
    }

    public void seek_frame() {
        long seek_target = (long) (seek_seconds * 1000000);
        videoDecoder.beforeSeek();
        videoDecoder.SeekVideoFrame(seek_target, 0);
        audioDecoder.beforeSeek();
        audioDecoder.SeekVideoFrame(seek_target, 0);

        if (mUploaderCallback != null) {
            mUploaderCallback.onSeekCallback(seek_seconds);
        } else {
            Log.e(TAG, "VideoDecoder::mUploaderCallback is NULL");
        }

        seek_resp = true;
    }

    public boolean isVideoEOF() {
        return isVideoOutputEOF;
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
}
