package com.lvpengwei.androidvideoplayer.sync;

import com.lvpengwei.androidvideoplayer.opengl.media.render.EglCore;
import com.lvpengwei.androidvideoplayer.opengl.media.texture.uploader.TextureFrameUploader;

public class UploaderCallbackImpl implements TextureFrameUploader.UploaderCallback {
    private AVSynchronizer mParent;

    public void setParent(AVSynchronizer mParent) {
        this.mParent = mParent;
    }

    @Override
    public void processVideoFrame(int inputTexId, int width, int height, float position) {
        if (mParent == null) return;
        mParent.processVideoFrame(inputTexId, width, height, position);
    }

    @Override
    public int processAudioData(short sample, int size, float position, byte buffer) {
        if (mParent == null) return -1;
        return mParent.processAudioData(sample, size, position, buffer);
    }

    @Override
    public void onSeekCallback(float seek_seconds) {
        if (mParent == null) return;
        mParent.onSeek(seek_seconds);
    }

    @Override
    public void initFromUploaderGLContext(EglCore eglCore) {
        if (mParent == null) return;
        int videoFrameWidth = mParent.getVideoFrameWidth();
        int videoFrameHeight = mParent.getVideoFrameHeight();

        mParent.OnInitFromUploaderGLContext(eglCore, videoFrameWidth, videoFrameHeight);
    }

    @Override
    public void destroyFromUploaderGLContext() {
        if (mParent == null) return;
        mParent.onDestroyFromUploaderGLContext();
    }
}
