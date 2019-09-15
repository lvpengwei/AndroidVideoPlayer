package com.lvpengwei.androidvideoplayer.opengl.media.texture.uploader;

import com.lvpengwei.androidvideoplayer.opengl.media.texture.GPUTextureFrame;
import com.lvpengwei.androidvideoplayer.opengl.media.texture.copier.GPUTextureFrameCopier;

public class GPUTextureFrameUploader extends TextureFrameUploader {
    int decodeTexId;

    public GPUTextureFrameUploader() {
        decodeTexId = 0;
    }

    public int getDecodeTexId() {
        return decodeTexId;
    }

    @Override
    public boolean initialize() {
        super.initialize();
        GPUTextureFrame textureFrame = new GPUTextureFrame();
        this.textureFrame = textureFrame;
        textureFrame.createTexture();
        decodeTexId = textureFrame.getDecodeTexId();
        textureFrameCopier = new GPUTextureFrameCopier();
        textureFrameCopier.init();
        return true;
    }

    @Override
    public void destroy() {
        if (textureFrameCopier != null) {
            textureFrameCopier.destroy();
        }
        if (textureFrame != null) {
            ((GPUTextureFrame) textureFrame).dealloc();
        }
        super.destroy();
    }
}
