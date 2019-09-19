package com.lvpengwei.androidvideoplayer.decoder;

import android.content.res.AssetFileDescriptor;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;

import com.lvpengwei.androidvideoplayer.opengl.media.texture.TextureFrame;
import com.lvpengwei.androidvideoplayer.opengl.media.texture.uploader.GPUTextureFrameUploader;
import com.lvpengwei.androidvideoplayer.opengl.media.texture.uploader.TextureFrameUploader;
import com.lvpengwei.androidvideoplayer.player.common.FrameTexture;

import java.io.IOException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static android.media.MediaFormat.KEY_HEIGHT;
import static android.media.MediaFormat.KEY_WIDTH;

public class VideoReader {
    private static String TAG = "VideoReader";

    private float seek_seconds;
    private boolean seek_req;
    private boolean seek_resp;
    private TextureFrameUploader textureFrameUploader;
    private Lock mLock;
    private Condition mCondition;
    private TextureFrameUploader.UploaderCallback mUploaderCallback;
    private int width;
    private int height;
    private int degress;
    private boolean isVideoOutputEOF;
    private MediaCodecVideoDecoder videoDecoder;
    private int decodeTexId;
    private boolean isMediaCodecInit;
    private AssetFileDescriptor fd;

    public boolean startReading(AssetFileDescriptor afd) {
        this.fd = afd;
        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        int trackIndex = 0;
        int numTracks = extractor.getTrackCount();
        for (int i = 0; i < numTracks; i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime.startsWith("video/")) {
                trackIndex = i;
                break;
            }
        }
        MediaFormat format = extractor.getTrackFormat(trackIndex);
        width = format.getInteger(KEY_WIDTH);
        height = format.getInteger(KEY_HEIGHT);
//        degress = format.getInteger(KEY_ROTATION);
        mLock = new ReentrantLock();
        mCondition = mLock.newCondition();
        videoDecoder = new MediaCodecVideoDecoder();
        isVideoOutputEOF = false;
        return true;
    }

    public void stopReading() {
        videoDecoder.CloseFile();
    }

    public int getVideoFrameHeight() {
        return height;
    }

    public int getVideoFrameWidth() {
        return width;
    }

    public FrameTexture copyNextSample() {
        if (!isMediaCodecInit) {
            initializeMediaCodec();
            isMediaCodecInit = true;
        }
        int ret = videoDecoder.GetNextVideoFrameForPlayback();
        if (ret == MediaCodecVideoDecoder.ERROR_OK) {
            uploadTexture();
            return null;
        } else if (ret == MediaCodecVideoDecoder.ERROR_EOF) {
            isVideoOutputEOF = true;
            return FrameTexture.EOF();
        } else {
            Log.e(TAG, "decode video error");
            return FrameTexture.ERROR();
        }
    }

    private void initializeMediaCodec() {
        decodeTexId = ((GPUTextureFrameUploader) textureFrameUploader).getDecodeTexId();
        videoDecoder.OpenFile(fd, decodeTexId);
    }

    private TextureFrameUploader createTextureFrameUploader() {
        return new GPUTextureFrameUploader();
    }

    private float updateTexImage(TextureFrame textureFrame) {
        return (float) (videoDecoder.updateTexImage() * 0.001 * 0.001);
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

    private void signalDecodeThread() {
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
