package com.lvpengwei.androidvideoplayer.opengl.media.texture;

import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.util.Log;

import com.lvpengwei.androidvideoplayer.opengl.media.render.GLTools;

public class GPUTextureFrame extends TextureFrame {
    private static String TAG = "GPUTextureFrame";
    private int decodeTexId = 0;

    public int getDecodeTexId() {
        return decodeTexId;
    }

    public boolean createTexture() {
        Log.i(TAG, "enter GPUTextureFrame::createTexture");
        decodeTexId = 0;
        int ret = initTexture();
        if (ret < 0) {
            Log.i(TAG, "init texture failed...");
            dealloc();
            return false;
        }
        return true;
    }

    public void updateTexImage() {

    }

    public boolean bindTexture(int uniformSamplers) {
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, decodeTexId);
        GLES20.glUniform1i(uniformSamplers, 0);
        return true;
    }

    private int initTexture() {
        int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);

        int textureId = textures[0];
        int type = GLES11Ext.GL_TEXTURE_EXTERNAL_OES;
        GLES20.glBindTexture(type, textureId);
        if (GLTools.checkEglError("glBindTexture")) {
            return -1;
        }
        GLES20.glTexParameterf(type, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        if (GLTools.checkEglError("glTexParameteri")) {
            return -1;
        }
        GLES20.glTexParameterf(type, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        if (GLTools.checkEglError("glTexParameteri")) {
            return -1;
        }
        GLES20.glTexParameteri(type, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        if (GLTools.checkEglError("glTexParameteri")) {
            return -1;
        }
        GLES20.glTexParameteri(type, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        if (GLTools.checkEglError("glTexParameteri")) {
            return -1;
        }
        decodeTexId = textureId;
        return 0;
    }

    public void dealloc() {
        Log.i(TAG, "enter GPUTextureFrame::dealloc");
        if (decodeTexId == 0) return;
        int[] textures = {decodeTexId};
        GLES20.glDeleteTextures(1, textures, 0);
    }
}
