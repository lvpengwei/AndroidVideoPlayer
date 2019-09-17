package com.lvpengwei.androidvideoplayer.decoder;

import android.content.res.AssetFileDescriptor;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;

import com.lvpengwei.androidvideoplayer.opengl.media.render.EglCore;
import com.lvpengwei.androidvideoplayer.opengl.media.render.VideoGLSurfaceRender;
import com.lvpengwei.androidvideoplayer.opengl.media.texture.TextureFrame;
import com.lvpengwei.androidvideoplayer.opengl.media.texture.uploader.GPUTextureFrameUploader;
import com.lvpengwei.androidvideoplayer.opengl.media.texture.uploader.TextureFrameUploader;

import java.io.IOException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static android.media.MediaFormat.KEY_HEIGHT;
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
    private boolean isVideoOutputEOF;
    private MediaCodecVideoDecoder videoDecoder;
    private int decodeTexId;
    private boolean isMediaCodecInit;
    private SampleBuffer buffer;
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
        startUploader(new TextureFrameUploader.UploaderCallback() {
            @Override
            public void processVideoFrame(int inputTexId, int width, int height, float position) {
                buffer = new SampleBuffer();
                buffer.position = position;
                buffer.texID = inputTexId;
                Log.i(TAG, "");
            }

            @Override
            public int processAudioData(short sample, int size, float position, byte buffer) {
                return 0;
            }

            @Override
            public void onSeekCallback(float seek_seconds) {

            }

            @Override
            public void initFromUploaderGLContext(EglCore eglCore) {
            }

            @Override
            public void destroyFromUploaderGLContext() {
            }
        });
        return true;
    }


    public void stopReading() {
        videoDecoder.CloseFile();
    }

    public SampleBuffer copyNextSample() {
        if (!isMediaCodecInit) {
            initializeMediaCodec();
            isMediaCodecInit = true;
        }
        int ret = videoDecoder.GetNextVideoFrameForPlayback();
        if (ret == MediaCodecVideoDecoder.ERROR_OK) {
            uploadTexture();
            return buffer;
        } else if (ret == MediaCodecVideoDecoder.ERROR_EOF) {
            isVideoOutputEOF = true;
            return SampleBuffer.EOF();
        } else {
            Log.e(TAG, "decode video error");
            return SampleBuffer.ERROR();
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
        return videoDecoder.updateTexImage();
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

    private void signalDecodeThread() {
        mLock.lock();
        mCondition.signal();
        mLock.unlock();
    }

    public boolean isVideoEOF() {
        return isVideoOutputEOF;
    }

}
