package com.lvpengwei.androidvideoplayer.decoder;

import android.opengl.GLES20;
import android.support.annotation.NonNull;

import com.lvpengwei.androidvideoplayer.opengl.media.render.GLTools;

/**
 * Author: pengweilv
 * Date: 2019-09-17
 * Description:
 */
public class SampleBuffer {
    private static int EOF = -1;
    private static int ERROR = -2;
    public int texID;
    public float position;
    public int width;
    public int height;

    public SampleBuffer() {
        this(0);
    }

    private SampleBuffer(int texID) {
        this(texID, 0);
    }

    public SampleBuffer(int texID, float position) {
        this.texID = texID;
        this.position = position;
    }

    public float getPosition() {
        return position;
    }

    public boolean isEOF() {
        return texID == EOF;
    }

    public boolean isError() {
        return texID == ERROR;
    }

    public static SampleBuffer EOF() {
        return new SampleBuffer(EOF);
    }

    public static SampleBuffer ERROR() {
        return new SampleBuffer(ERROR);
    }

    public void buildGPUFrame(int width, int height) {
        this.width = width;
        this.height = height;
        int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);

        int texId = textures[0];
        GLTools.checkEglError("glGenTextures texId");
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId);
        GLTools.checkEglError("glBindTexture texId");
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        int internalFormat = GLES20.GL_RGBA;
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, internalFormat, width, height, 0, internalFormat, GLES20.GL_UNSIGNED_BYTE, null);

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        this.texID = texId;
    }

    public void dealloc() {
        deleteTex();
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        deleteTex();
    }

    private void deleteTex() {
        if (texID <= 0) return;
        int[] textures = {texID};
        GLES20.glDeleteTextures(1, textures, 0);
        texID = 0;
    }

    @NonNull
    @Override
    public String toString() {
        return "SampleBuffer{" +
                "texID=" + texID +
                ", position=" + position +
                '}';
    }
}
