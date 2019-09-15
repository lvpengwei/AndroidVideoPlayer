package com.lvpengwei.androidvideoplayer.opengl.media;

public abstract class MovieFrame {
    public float position = 0;
    public float duration = 0;

    public abstract MovieFrameType getType();
}
