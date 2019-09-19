package com.lvpengwei.androidvideoplayer.player;

import android.content.res.AssetFileDescriptor;
import android.opengl.EGLContext;
import android.util.Log;
import android.view.Surface;

import com.lvpengwei.androidvideoplayer.decoder.DecoderRequestHeader;
import com.lvpengwei.androidvideoplayer.player.common.FrameTexture;
import com.lvpengwei.androidvideoplayer.sync.AVSynchronizer;

/**
 * Author: pengweilv
 * Date: 2019-09-18
 * Description:
 */
public class PlayerController {
    private static String TAG = "PlayerController ";
    private boolean isPlaying;
    private float minBufferedDuration;
    private float maxBufferedDuration;
    private AVSynchronizer synchronizer;
    private VideoOutput videoOutput;
    private AudioOutput audioOutput;
    private DecoderRequestHeader requestHeader = new DecoderRequestHeader();
    private final Object mLock = new Object();

    public void init(AssetFileDescriptor fd) {
        requestHeader.fd = fd;
        isPlaying = false;
        userCancelled = false;
        new Thread(new Runnable() {
            @Override
            public void run() {
                startAVSynchronizer();
                synchronized (mLock) {
                    mLock.notify();
                }
            }
        }).start();
    }

    public boolean isPlaying() {
        return isPlaying;
    }

    public void play() {
        Log.i(TAG, "VideoPlayerController::play " + isPlaying);
        if (isPlaying) return;
        isPlaying = true;
        if (null != audioOutput) {
            audioOutput.play();
        }
    }

    public void seekToPosition(float position) {
        if (synchronizer == null) return;
        synchronizer.seekToPosition(position);
    }

    public void pause() {
        Log.i(TAG, "VideoPlayerController::pause");
        if (!isPlaying) return;
        isPlaying = false;
        if (null != audioOutput) {
            audioOutput.pause();
        }
    }

    public void destroy() {
        Log.i(TAG, "enter VideoPlayerController::destroy...");

        userCancelled = true;

        if (synchronizer != null) {
            //中断request
            synchronizer.interruptRequest();
        }

        synchronized (mLock) {
            try {
                mLock.wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        if (null != videoOutput) {
            videoOutput.stopOutput();
        }

        if (null != synchronizer) {
            synchronizer.isDestroyed = true;
            pause();
            Log.i(TAG, "stop synchronizer ...");
            synchronizer.destroy();

            Log.i(TAG, "stop audioOutput ...");
            if (null != audioOutput) {
                audioOutput.stop();
            }
//            synchronizer.clearFrameMeta();
        }

        Log.i(TAG, "leave VideoPlayerController::destroy...");
    }

    public float getDuration() {
        return 0;
    }

    public int getVideoFrameWidth() {
        return 0;
    }

    public int getVideoFrameHeight() {
        return 0;
    }

    //获得缓冲进度
    public float getBufferedProgress() {
        return 0;
    }

    //获得播放进度
    public float getPlayProgress() {
        return 0;
    }

    /**
     * 重置播放区域的大小,比如横屏或者根据视频的ratio来调整
     **/
    public void resetRenderSize(int left, int top, int width, int height) {
        Log.i(TAG, "VideoPlayerController::resetRenderSize");
        if (null != videoOutput) {
            Log.i(TAG, "VideoPlayerController::resetRenderSize NULL != videoOutput width:" + width + ", height:" + height);
            videoOutput.resetRenderSize(left, top, width, height);
        } else {
            Log.i(TAG, "VideoPlayerController::resetRenderSize NULL == videoOutput width:" + width + ", height:" + height);
            screenWidth = width;
            screenHeight = height;
        }
    }

    public int getScreenWidth() {
        if (videoOutput == null) return 0;
        return videoOutput.getScreenWidth();
    }

    public int getScreenHeight() {
        if (videoOutput == null) return 0;
        return videoOutput.getScreenHeight();
    }

    private boolean startAVSynchronizer() {
        Log.i(TAG, "enter VideoPlayerController::startAVSynchronizer...");
        if (userCancelled) {
            return false;
        }

        boolean ret = false;
        if (initAVSynchronizer()) {
            if (synchronizer.validAudio()) {
                ret = this.initAudioOutput();
            }
        }
        if (ret) {
            if (null != synchronizer && !synchronizer.isValid()) {
                ret = false;
            } else {
                isPlaying = true;
                synchronizer.start();
                Log.i(TAG, "call audioOutput start...");
                if (null != audioOutput) {
                    audioOutput.start();
                }
                Log.i(TAG, "After call audioOutput start...");
            }
        }

        Log.i(TAG, "VideoPlayerController::startAVSynchronizer() init result: " + (ret ? "success" : "fail"));
//        this->setInitializedStatus(ret);

        return ret;
    }

    private Surface surface;
    private boolean userCancelled;

    public void onSurfaceCreated(Surface surface, int width, int height) {
        this.surface = surface;
        if (userCancelled) return;

        if (width > 0 && height > 0) {
            this.screenHeight = height;
            this.screenWidth = width;
        }
        if (null == videoOutput) {
            initVideoOutput(surface);
        } else {
            videoOutput.onSurfaceCreated(surface);
        }
        Log.i(TAG, "Leave VideoPlayerController::onSurfaceCreated...");
    }

    public void onSurfaceDestroyed() {
        if (videoOutput == null) return;
        videoOutput.onSurfaceDestroyed();
    }

    private void signalOutputFrameAvailable() {
        videoOutput.signalFrameAvailable();
    }

    EGLContext getUploaderEGLContext() {
        return null;
    }

    private boolean initAudioOutput() {
        Log.i(TAG, "VideoPlayerController::initAudioOutput");

        int channels = getAudioChannels();
        if (channels < 0) {
            Log.i(TAG, "VideoDecoder get channels failed ...");
            return false;
        }
        int sampleRate = synchronizer.getAudioSampleRate();
        if (sampleRate < 0) {
            Log.i(TAG, "VideoDecoder get sampleRate failed ...");
            return false;
        }
        audioOutput = new AudioOutput();
        audioOutput.init(channels, sampleRate, new AudioOutput.AudioOutputCallback() {
            @Override
            public byte[] produceData(int bufferSize) {
                if (isPlaying && synchronizer != null && !synchronizer.isDestroyed && !synchronizer.isPlayCompleted()) {
                    signalOutputFrameAvailable();
                    return synchronizer.fillAudioData(bufferSize);
                } else {
                    return new byte[bufferSize];
                }
            }
        });
        return true;
    }

    private int getAudioChannels() {
        return synchronizer.getAudioChannels();
    }

    private int screenWidth;
    private int screenHeight;

    private void initVideoOutput(Surface surface) {
        videoOutput = new VideoOutput();
        videoOutput.init(surface, screenWidth, screenHeight, new VideoOutput.VideoOutputCallback() {
            @Override
            public FrameTexture getTexture(Object ctx, boolean forceGetFrame) {
                return getCorrectRenderTexture(forceGetFrame);
            }
        }, this);
    }

    private FrameTexture getCorrectRenderTexture(boolean forceGetFrame) {
        FrameTexture texture = null;
        if (!synchronizer.isDestroyed) {
            if (synchronizer.isPlayCompleted()) {
                Log.i(TAG, "Video Render Thread render Completed We will Render First Frame...");
                texture = synchronizer.getFirstRenderTexture();
            } else {
                texture = synchronizer.getCorrectRenderTexture(forceGetFrame);
            }
        }
        return texture;
    }

    private boolean initAVSynchronizer() {
        synchronizer = new AVSynchronizer();
        return synchronizer.init(requestHeader, minBufferedDuration, maxBufferedDuration);
    }
}
