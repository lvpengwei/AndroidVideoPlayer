package com.lvpengwei.androidvideoplayer.player.common;

import android.opengl.GLES20;

public class FrameTexture {
    public static int INVALID_FRAME_POSITION = -1;
    public int texId;
    public float position;
    public int width;
    public int height;

    public FrameTexture() {
        texId = 0;
        position = INVALID_FRAME_POSITION;
        width = 0;
        height = 0;
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        if (texId > 0) {
            int[] textures = {texId};
            GLES20.glDeleteTextures(1, textures, 0);
        }
    }
}
