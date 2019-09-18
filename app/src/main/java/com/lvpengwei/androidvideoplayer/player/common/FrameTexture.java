package com.lvpengwei.androidvideoplayer.player.common;

import android.opengl.GLES20;
import android.support.annotation.NonNull;

public class FrameTexture {

    public static int INVALID_FRAME_POSITION = -1;
    private static int EOF = -2;
    private static int ERROR = -3;

    public int texId;
    public float position;
    public int width;
    public int height;

    public FrameTexture() {
        this(0);
    }

    private FrameTexture(int texID) {
        this(texID, 0);
    }

    public FrameTexture(int texID, float position) {
        this.texId = texID;
        this.position = position;
    }

    public float getPosition() {
        return position;
    }

    public boolean isEOF() {
        return texId == EOF;
    }

    public boolean isError() {
        return texId == ERROR;
    }

    public static FrameTexture EOF() {
        return new FrameTexture(EOF);
    }

    public static FrameTexture ERROR() {
        return new FrameTexture(ERROR);
    }

    public void dealloc() {
        deleteTex();
    }

    private void deleteTex() {
        if (texId <= 0) return;
        int[] textures = {texId};
        GLES20.glDeleteTextures(1, textures, 0);
        texId = 0;
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        dealloc();
    }

    @NonNull
    @Override
    public String toString() {
        return "FrameTexture{" +
                "texID=" + texId +
                ", position=" + position +
                '}';
    }
}
