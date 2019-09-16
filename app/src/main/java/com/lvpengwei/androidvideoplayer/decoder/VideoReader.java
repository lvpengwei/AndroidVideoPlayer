package com.lvpengwei.androidvideoplayer.decoder;

import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;

import com.lvpengwei.androidvideoplayer.opengl.media.texture.TextureFrame;
import com.lvpengwei.androidvideoplayer.opengl.media.texture.uploader.GPUTextureFrameUploader;
import com.lvpengwei.androidvideoplayer.opengl.media.texture.uploader.TextureFrameUploader;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static android.media.MediaFormat.KEY_HEIGHT;
import static android.media.MediaFormat.KEY_ROTATION;
import static android.media.MediaFormat.KEY_WIDTH;

public class VideoReader {
    private static String TAG = "VideoReader";
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
    private MediaCodecVideoDecoder videoDecoder;
    private int decodeTexId;
    private boolean isMediaCodecInit;
    private MediaExtractor extractor;
    public VideoReader() {
    }

    public void openFile(MediaExtractor extractor, int trackIndex) {
        this.extractor = extractor;
        MediaFormat format = extractor.getTrackFormat(trackIndex);
        width = format.getInteger(KEY_WIDTH);
        height = format.getInteger(KEY_HEIGHT);
        degress = format.getInteger(KEY_ROTATION);
        seek_req = false;
        seek_resp = false;
        mLock = new ReentrantLock();
        mCondition = mLock.newCondition();
        videoDecoder = new MediaCodecVideoDecoder();
        isVideoOutputEOF = false;
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


    private void initializeMediaCodec() {
        decodeTexId = ((GPUTextureFrameUploader) textureFrameUploader).getDecodeTexId();
        videoDecoder.OpenFile(extractor.path, decodeTexId);
    }

    private TextureFrameUploader createTextureFrameUploader() {
        return new GPUTextureFrameUploader();
    }

    private float updateTexImage(TextureFrame textureFrame) {
        return videoDecoder.updateTexImage();
    }

    public void closeFile() {
        videoDecoder.CloseFile();
    }

    private void startUploader(TextureFrameUploader.UploaderCallback uploaderCallback) {
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

    private void uploadTexture() {
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
    }

    public void seek_frame() {
        long seek_target = (long) (seek_seconds * 1000000);
        videoDecoder.beforeSeek();
        videoDecoder.SeekVideoFrame(seek_target, 0);

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

    public boolean hasSeekReq() {
        return seek_req;
    }

    public boolean hasSeekResp() {
        return seek_resp;
    }
}
